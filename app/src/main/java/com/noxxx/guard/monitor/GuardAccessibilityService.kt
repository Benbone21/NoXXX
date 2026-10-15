package com.noxxx.guard.monitor

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.R
import com.noxxx.guard.data.BlockKind
import com.noxxx.guard.policy.Policy
import com.noxxx.guard.policy.Sensitivity
import com.noxxx.guard.vision.ScanResult
import com.noxxx.guard.vision.ScreenScanner
import com.noxxx.guard.web.PrivateDns
import com.noxxx.guard.web.UrlChecker
import com.noxxx.guard.web.Verdict

/**
 * The always-on guard. Watches which app is in front and what browsers show in their address bar,
 * periodically classifies the screen on-device, and protects NoXXX's own settings.
 */
class GuardAccessibilityService : AccessibilityService() {
    private val app get() = NoXXXApp.instance
    private val policy: Policy get() = app.store.policy

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: BlockOverlay
    private lateinit var shield: TamperShield
    private lateinit var browsers: BrowserRegistry
    private lateinit var power: PowerManager
    private var scanner: ScreenScanner? = null

    private var foreground: String? = null
    /** Class name of the last window that came to the front, used to scope setup grace. */
    private var lastWindowClass: String? = null
    /** Latest screen/pane title announced by each package (e.g. "Accessibility", "NoXXX"). */
    private val titleByPackage = HashMap<String, String>()
    /** Windows that pop over the real foreground app (keyboard, system UI). */
    private var transient: Set<String> = emptySet()
    /** Apps never screen-scanned (launcher, keyboard, system UI, NoXXX). */
    private var scanExempt: Set<String> = emptySet()
    /** Essential apps that can never be app-blocked (phone, texts, settings, launcher, keyboard…). */
    private var essential: Set<String> = emptySet()

    private var flaggedFrames = 0
    private var blankFrames = 0
    private var lastWindowCheck = 0L
    private var windowCheckPosted = false
    private var watchdogTicks = 0
    private val appLabel by lazy { getString(R.string.app_name) }

    private var tamperCheckPosted = false
    private var shieldSince = 0L
    /** Raised by a tap on NoXXX's name: hold the shield until the screen that tap opens can be judged. */
    private var awaitingNextScreen = false
    /** One clear check has passed; lower the shield if the next one passes too. */
    private var clearPending = false
    private var shieldBacks = 0
    private var shieldReset = false
    private var shieldLogged = false
    private var lastShieldActionAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        TamperNotifier.dismiss(this)
        overlay = BlockOverlay(this)
        shield = TamperShield(this)
        browsers = BrowserRegistry(this)
        power = getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) scanner = ScreenScanner(this)
        refreshPackageSets()
        app.startClock()
        handler.post(scanLoop)
        handler.post(watchdog)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) foreground = pkg
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastWindowClass = event.className?.toString()
            // Always replace, so a stale "NoXXX" title can't outlive the screen it came from.
            val title = event.text?.joinToString(" ")?.takeIf { it.isNotBlank() }
            if (title != null) titleByPackage[pkg] = title else titleByPackage.remove(pkg)
            if (pkg !in transient && pkg != foreground) {
                foreground = pkg
                flaggedFrames = 0
                blankFrames = 0
            }
            onForegroundApp(pkg)
        }
        if (pkg in TamperGuard.GUARDED_PACKAGES) runCatching { onGuardedEvent(event, pkg) }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) runCatching { checkSearchField(event, pkg) }
        scheduleWindowCheck()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.dismiss()
        if (::shield.isInitialized) shield.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) scanner?.close()
        super.onDestroy()
    }

    // --- App-level rules (no window content needed) ---

    private fun onForegroundApp(pkg: String) {
        if (overlay.isShowing || pkg in transient) return
        val state = app.store.state.value
        val p = state.policy
        if (pkg in p.blockedApps && pkg !in essential && pkg !in p.neverBlockApps) return block(BlockKind.APP, label(pkg))
        if (browsers.isBrowser(pkg)) {
            if (p.blockUnsupportedBrowsers && !browsers.isSupported(pkg)) return block(BlockKind.BROWSER, label(pkg))
            if (state.committed && p.requirePrivateDns && !privateDnsOk(p)) {
                // Re-check shortly: Private DNS briefly reads as inactive while switching networks.
                handler.postDelayed({
                    if (foreground == pkg && !overlay.isShowing && !privateDnsOk(policy)) block(BlockKind.DNS_OFF, label(pkg))
                }, DNS_RECHECK_MS)
            }
        }
    }

    private fun privateDnsOk(p: Policy): Boolean {
        if (PrivateDns.isCompliant(this)) return true
        // With the optional adb grant, repair it silently and re-check.
        return PrivateDns.enforce(this, p.privateDnsHost) && PrivateDns.isCompliant(this)
    }

    // --- Window content rules (throttled) ---

    private fun scheduleWindowCheck() {
        if (windowCheckPosted) return
        windowCheckPosted = true
        val wait = (lastWindowCheck + WINDOW_CHECK_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed({
            windowCheckPosted = false
            lastWindowCheck = SystemClock.uptimeMillis()
            runCatching { checkActiveWindow() }
        }, wait)
    }

    private fun checkActiveWindow() {
        if (overlay.isShowing || shield.isShowing) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg in TamperGuard.GUARDED_PACKAGES) {
            if (isLockedScreen(root, pkg)) onLockedScreen(pkg)
            return
        }
        val p = policy

        if (p.urlFilter && browsers.isSupported(pkg)) {
            val keywords = app.keywords.matcherFor(p)
            for (id in browsers.addressBarIds(pkg)) {
                for (node in root.findAccessibilityNodeInfosByViewId(id)) {
                    val text = node.text?.toString() ?: continue
                    when (val v = UrlChecker.check(text, p, app.blocklist, keywords)) {
                        is Verdict.Domain -> return block(BlockKind.WEBSITE, v.host)
                        Verdict.Keyword -> {
                            if (node.isEditable) clearText(node)
                            return block(BlockKind.SEARCH, label(pkg))
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    // --- Search boxes in any app ---

    /** Checks text typed into search-like fields (not messages or passwords) against the word list. */
    private fun checkSearchField(event: AccessibilityEvent, pkg: String) {
        val p = policy
        if (!p.checkSearchBoxes || event.isPassword || overlay.isShowing || pkg in transient) return
        val node = event.source ?: return
        if (!node.isEditable || node.isPassword || node.isShowingHintText) return
        if (!looksLikeSearch(node)) return
        val text = node.text?.toString() ?: return
        if (!app.keywords.matcherFor(p).matches(text)) return
        clearText(node)
        block(BlockKind.SEARCH, label(pkg))
    }

    /**
     * True for search boxes and address bars only. Anything that looks like a place to write to people
     * (messages, replies, comments, captions, posts, notes) is skipped even if it also mentions search,
     * so normal conversation is never checked.
     */
    private fun looksLikeSearch(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        val hint = node.hintText?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val cls = node.className?.toString().orEmpty()
        if (CONVERSATION_HINTS.any { it in id || it in hint || it in desc }) return false
        return SEARCH_ID_HINTS.any { it in id } || "search" in hint || "search" in desc || "SearchView" in cls
    }

    private fun clearText(node: AccessibilityNodeInfo) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "") }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // --- Screen scanning ---

    private val scanLoop = object : Runnable {
        override fun run() {
            runCatching { maybeScan() }
            handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    private fun maybeScan() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val sc = scanner ?: return
        val pkg = foreground ?: return
        if (overlay.isShowing || !power.isInteractive || pkg in scanExempt) return
        val p = policy
        val wantImage = p.imageScan && pkg !in p.imageScanExemptApps &&
            (p.scanMessagingApps || !CommApps.isCommApp(pkg))
        // Block FLAG_SECURE (anti-screenshot) in any non-exempt app, not just browsers.
        // Catches anonymous/private modes in social apps that use FLAG_SECURE to hide content.
        val wantPrivate = p.blockPrivateBrowsing && !SecureScreenExemptions.isExempt(pkg)
        if (!wantImage && !wantPrivate) return
        val sensitivity = p.sensitivity
        sc.scan(sensitivity) { result ->
            handler.post { onScanResult(pkg, result, wantImage, wantPrivate, sensitivity) }
        }
    }

    private fun onScanResult(pkg: String, r: ScanResult, wantImage: Boolean, wantPrivate: Boolean, s: Sensitivity) {
        if (pkg != foreground || overlay.isShowing) return
        if (r.blank) {
            blankFrames++
            flaggedFrames = 0
        } else {
            blankFrames = 0
            flaggedFrames = if (r.flagged) flaggedFrames + 1 else 0
        }
        val needed = if (s == Sensitivity.STRICT) 1 else 2
        when {
            wantImage && flaggedFrames >= needed -> block(BlockKind.IMAGE, label(pkg))
            wantPrivate && blankFrames >= 3 -> block(BlockKind.PRIVATE_BROWSING, label(pkg))
        }
    }

    // --- Housekeeping ---

    private val watchdog = object : Runnable {
        override fun run() {
            runCatching {
                val s = app.store.state.value
                if (!s.settingsEditable && s.policy.requirePrivateDns) PrivateDns.enforce(this@GuardAccessibilityService, s.policy.privateDnsHost)
                if (++watchdogTicks % 10 == 0) {
                    browsers.refresh()
                    refreshPackageSets()
                }
            }
            handler.postDelayed(this, 30_000)
        }
    }

    private fun refreshPackageSets() {
        val imes = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        @Suppress("DEPRECATION")
        val homes = runCatching {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        transient = (imes + "com.android.systemui").toSet()
        scanExempt = transient + homes + packageName
        essential = EssentialApps.protectedSet(this)
    }

    private fun block(kind: BlockKind, detail: String) {
        if (overlay.isShowing) return
        app.blockLog.add(kind, detail)
        when (kind) {
            BlockKind.WEBSITE, BlockKind.SEARCH, BlockKind.IMAGE, BlockKind.PRIVATE_BROWSING -> performGlobalAction(GLOBAL_ACTION_BACK)
            else -> performGlobalAction(GLOBAL_ACTION_HOME)
        }
        flaggedFrames = 0
        blankFrames = 0
        overlay.show(kind, detail, streakDays()) { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    // --- Tamper protection ---
    // New screens in system settings are judged on the event itself, not behind the window-check
    // throttle. A locked screen is covered by the shield first, then backed out of underneath it.

    /** TamperShield is active during setup lock and regular unlock window; only off during full unlock. */
    private fun tamperLocked(): Boolean = app.store.state.value.let { !it.tamperShieldOff && it.policy.tamperProtection }

    private fun onGuardedEvent(event: AccessibilityEvent, pkg: String) {
        if (!tamperLocked()) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                if (clickNamesApp(event) && !TamperGuard.allowsWindow(lastWindowClass)) raiseShield(awaitNextScreen = true)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                awaitingNextScreen = false
                // Check synchronously: show shield immediately on sensitive screen to close the tap gap.
                val root = frontRoot()
                val rootPkg = root?.packageName?.toString()
                if (root != null && rootPkg != null && isLockedScreen(root, rootPkg)) {
                    onLockedScreen(rootPkg)
                } else {
                    checkTamperNow()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (event.contentChangeTypes and PANE_CHANGES != 0) {
                awaitingNextScreen = false
                val root = frontRoot()
                val rootPkg = root?.packageName?.toString()
                if (root != null && rootPkg != null && isLockedScreen(root, rootPkg)) {
                    onLockedScreen(rootPkg)
                } else {
                    checkTamperNow()
                }
            }
        }
    }

    private fun clickNamesApp(event: AccessibilityEvent): Boolean {
        if (event.text.any { TamperRules.mentions(it, appLabel) } || TamperRules.mentions(event.contentDescription, appLabel)) return true
        val src = event.source ?: return false
        return TamperRules.mentions(src.text, appLabel) || TamperRules.mentions(src.contentDescription, appLabel)
    }

    private val tamperCheck = Runnable {
        tamperCheckPosted = false
        runCatching { evaluateTamper() }
    }

    /** Runs on the next main-loop pass so a burst of events from one screen change is checked once. */
    private fun checkTamperNow() {
        if (tamperCheckPosted) return
        tamperCheckPosted = true
        handler.post(tamperCheck)
    }

    private fun isLockedScreen(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg !in TamperGuard.GUARDED_PACKAGES || !tamperLocked() || TamperGuard.allowsWindow(lastWindowClass)) return false
        val p = policy
        val dnsGrace = TamperGuard.activeGrace() == TamperGuard.Grace.PRIVATE_DNS
        val host = p.privateDnsHost.takeIf { p.requirePrivateDns && !dnsGrace }
        return TamperGuard.isSensitive(root, titleByPackage[pkg], appLabel, host)
    }

    /** The app window in front. Touching the shield can make it the "active" window, so look past it. */
    private fun frontRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.takeIf { it.packageName?.toString() != packageName }?.let { return it }
        return windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() != packageName }
    }

    private fun evaluateTamper() {
        val root = frontRoot()
        val pkg = root?.packageName?.toString()
        if (root != null && pkg != null && isLockedScreen(root, pkg)) return onLockedScreen(pkg)
        if (!shield.isShowing) return
        val waiting = awaitingNextScreen && SystemClock.uptimeMillis() - shieldSince < PREARM_MS
        if (!waiting) {
            if (clearPending) return lowerShield()
            clearPending = true
        }
        scheduleShieldPoll()
    }

    /**
     * Covers the screen, then presses Back (so you land on the list you came from and Settings can't
     * reopen on this page). If Back doesn't get off it, Settings is reset to its main page, then Home.
     */
    private fun onLockedScreen(pkg: String) {
        raiseShield(awaitNextScreen = false)
        awaitingNextScreen = false
        clearPending = false
        if (!shieldLogged) {
            shieldLogged = true
            app.blockLog.add(BlockKind.SETTINGS, label(pkg))
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastShieldActionAt >= SHIELD_ACTION_SPACING_MS) {
            lastShieldActionAt = now
            when {
                shieldBacks < MAX_SETTINGS_BACKS -> {
                    shieldBacks++
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                !shieldReset && pkg == SETTINGS_PACKAGE -> {
                    shieldReset = true
                    val reset = Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    runCatching { startActivity(reset) }.onFailure { performGlobalAction(GLOBAL_ACTION_HOME) }
                }
                else -> performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        scheduleShieldPoll()
    }

    private fun raiseShield(awaitNextScreen: Boolean) {
        if (!shield.isShowing) {
            shield.show()
            shieldSince = SystemClock.uptimeMillis()
            awaitingNextScreen = awaitNextScreen
            clearPending = false
        }
        scheduleShieldPoll()
    }

    private fun lowerShield() {
        handler.removeCallbacks(shieldPoll)
        shield.hide()
        awaitingNextScreen = false
        clearPending = false
        shieldBacks = 0
        shieldReset = false
        shieldLogged = false
        lastShieldActionAt = 0
    }

    private val shieldPoll = Runnable {
        // Safety valve: never leave a cover stuck on screen if something unexpected keeps it up.
        if (shield.isShowing && SystemClock.uptimeMillis() - shieldSince > SHIELD_MAX_MS) {
            lowerShield()
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            runCatching { evaluateTamper() }.onFailure { if (shield.isShowing) scheduleShieldPoll() }
        }
    }

    private fun scheduleShieldPoll() {
        handler.removeCallbacks(shieldPoll)
        handler.postDelayed(shieldPoll, SHIELD_POLL_MS)
    }

    private fun streakDays(): Long =
        ((System.currentTimeMillis() - app.store.state.value.streakStartWallMs) / 86_400_000L).coerceAtLeast(0)

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val SCAN_INTERVAL_MS = 1_500L
        private const val WINDOW_CHECK_INTERVAL_MS = 400L
        private const val DNS_RECHECK_MS = 3_000L
        private const val SHIELD_POLL_MS = 120L
        /** Roughly one Back animation, so a second Back isn't sent before the first has landed. */
        private const val SHIELD_ACTION_SPACING_MS = 450L
        private const val PREARM_MS = 900L
        private const val SHIELD_MAX_MS = 8_000L
        private const val MAX_SETTINGS_BACKS = 3
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val PANE_CHANGES =
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE
        private val SEARCH_ID_HINTS = listOf("search", "query", "url_bar", "url_field", "omnibar", "location_bar", "address_bar")
        private val CONVERSATION_HINTS = listOf("message", "compose", "chat", "reply", "comment", "caption", "post", "note", "status", "tweet")

        @Volatile
        var isRunning = false
            private set

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, GuardAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

package com.noxxx.guard.monitor

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Stops NoXXX from being switched off on impulse. While settings are locked, system screens that
 * can disable it (its own Accessibility page, App info, device-admin deactivation, the uninstall
 * prompt, the Private DNS dialog) are closed. Lists that merely include it stay usable.
 */
object TamperGuard {
    /** System packages that host screens able to disable, force-stop or uninstall the app. */
    val GUARDED_PACKAGES = setOf(
        "android", // accessibility shortcut chooser
        "com.android.settings",
        "com.google.android.settings.intelligence",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.accessibility",
        "com.samsung.android.lool", // Device care (can force-stop apps)
        "com.samsung.android.sm",
        "com.miui.securitycenter",
        "com.miui.packageinstaller",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
    )

    /**
     * A short pass for a system screen NoXXX itself opens during setup. Each kind only relaxes the
     * check it needs, so the pass can't be used to reach other screens.
     */
    enum class Grace(val windowClassHint: String?, val durationMs: Long = 45_000, val maxMs: Long = durationMs) {
        /** Private DNS dialog: allow editing the hostname. */
        PRIVATE_DNS(null),
        /**
         * Device admin activation screen. Only waives the title rule on a screen that shows activation
         * text and nothing that deactivates (see [TamperRules.reasonDuringAdminGrace]), so it is safe
         * to keep for a few minutes: long enough to read the screen and tap Activate.
         */
        DEVICE_ADMIN(TamperRules.ADMIN_WINDOW_HINT, durationMs = 120_000, maxMs = 600_000),
        /** Battery-optimization exemption prompt. */
        BATTERY("RequestIgnoreBatteryOptimizations"),
        /** Only offered while the guard service is off, so there is nothing to protect yet. */
        FULL(null),
    }

    private val grace = SetupGrace()

    private fun now() = SystemClock.elapsedRealtime()

    fun grantGrace(kind: Grace) = grace.grant(kind, now())

    /** Ends the pass. [keep] leaves a pass of that kind running (see MainActivity.onResume). */
    fun endGrace(keep: Grace? = null) = grace.end(keep)

    fun activeGrace(): Grace? = grace.active(now())

    /**
     * True if the current window may be shown despite naming the app, judged by window class alone.
     * The device-admin grace is not decided here: it needs the screen's content ([sensitiveReason]).
     */
    fun allowsWindow(windowClass: String?, title: String? = null): Boolean {
        val g = activeGrace() ?: return false
        if (g == Grace.FULL) return true
        if (g == Grace.DEVICE_ADMIN) return false
        return TamperRules.graceAllows(g.windowClassHint, false, windowClass, title)
    }

    /**
     * True if this system screen can switch NoXXX off (see [TamperRules]). Screens that merely list
     * the app, like the Accessibility list or app lists, are allowed.
     */
    fun isSensitive(root: AccessibilityNodeInfo, windowTitle: String?, appLabel: String, dnsHost: String?) =
        sensitiveReason(root, windowTitle, appLabel, dnsHost) != null

    /**
     * Which tamper rule matched this screen, or null if it's safe. While the device-admin grace is
     * active the whole screen is read so the activation screen can be told apart from deactivation;
     * seeing the activation screen keeps that grace alive.
     */
    fun sensitiveReason(
        root: AccessibilityNodeInfo, windowTitle: String?, appLabel: String, dnsHost: String?, windowClass: String? = null,
    ): TamperRules.Reason? {
        val pkg = root.packageName?.toString().orEmpty()
        if (activeGrace() == Grace.DEVICE_ADMIN) {
            val verdict = TamperRules.reasonDuringAdminGrace(collect(root), windowTitle, windowClass, appLabel, dnsHost, pkg)
            if (verdict.activation) grace.extend(Grace.DEVICE_ADMIN, now())
            return verdict.reason
        }
        val scan = TamperRules.Scan(windowTitle, appLabel, dnsHost, pkg)
        if (scan.sensitive) return scan.reason
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES) {
            val node = queue.removeFirst()
            if (scan.offer(facts(node))) return scan.reason
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    /** True if the device-admin grace is active and this is its activation screen. */
    fun isAdminActivationInFront(root: AccessibilityNodeInfo, windowTitle: String?, windowClass: String?, appLabel: String) =
        activeGrace() == Grace.DEVICE_ADMIN && TamperRules.isAdminActivation(collect(root), windowTitle, windowClass, appLabel)

    /** Every node's facts, breadth first, up to [MAX_NODES]. */
    fun collect(root: AccessibilityNodeInfo): List<NodeFacts> {
        val out = ArrayList<NodeFacts>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val node = queue.removeFirst()
            out += facts(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return out
    }

    private fun facts(node: AccessibilityNodeInfo) = NodeFacts(
        text = node.text?.toString(),
        description = node.contentDescription?.toString(),
        checkable = node.isCheckable,
        editable = node.isEditable,
    )

    private const val MAX_NODES = 800
}

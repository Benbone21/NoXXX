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
    enum class Grace(val windowClassHint: String?) {
        /** Private DNS dialog: allow editing the hostname. */
        PRIVATE_DNS(null),
        /** Device admin activation screen. */
        DEVICE_ADMIN("DeviceAdminAdd"),
        /** Battery-optimization exemption prompt. */
        BATTERY("RequestIgnoreBatteryOptimizations"),
        /** Only offered while the guard service is off, so there is nothing to protect yet. */
        FULL(null),
    }

    @Volatile
    private var grace: Grace? = null

    @Volatile
    private var graceUntil = 0L

    fun grantGrace(kind: Grace, ms: Long = 45_000) {
        grace = kind
        graceUntil = SystemClock.elapsedRealtime() + ms
    }

    fun endGrace() {
        grace = null
        graceUntil = 0
    }

    fun activeGrace(): Grace? = grace.takeIf { SystemClock.elapsedRealtime() < graceUntil }

    /** True if the current window may be shown despite naming the app. */
    fun allowsWindow(windowClass: String?): Boolean {
        val g = activeGrace() ?: return false
        if (g == Grace.FULL) return true
        val hint = g.windowClassHint ?: return false
        return windowClass?.contains(hint) == true
    }

    /**
     * True if this system screen can switch NoXXX off (see [TamperRules]). Screens that merely list
     * the app, like the Accessibility list or app lists, are allowed.
     */
    fun isSensitive(root: AccessibilityNodeInfo, windowTitle: String?, appLabel: String, dnsHost: String?) =
        sensitiveReason(root, windowTitle, appLabel, dnsHost) != null

    /** Which tamper rule matched this screen, or null if it's safe. */
    fun sensitiveReason(root: AccessibilityNodeInfo, windowTitle: String?, appLabel: String, dnsHost: String?): TamperRules.Reason? {
        val scan = TamperRules.Scan(windowTitle, appLabel, dnsHost, root.packageName?.toString().orEmpty())
        if (scan.sensitive) return scan.reason
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES) {
            val node = queue.removeFirst()
            val facts = NodeFacts(
                text = node.text?.toString(),
                description = node.contentDescription?.toString(),
                checkable = node.isCheckable,
                editable = node.isEditable,
            )
            if (scan.offer(facts)) return scan.reason
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private const val MAX_NODES = 800
}

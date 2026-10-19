package com.noxxx.guard.monitor

/** What TamperGuard looks at for each on-screen element. */
data class NodeFacts(
    val text: String?,
    val description: String?,
    val checkable: Boolean = false,
    val editable: Boolean = false,
)

/**
 * Decides whether a system screen can switch NoXXX off. Pure logic so it can be unit-tested.
 *
 * Just showing the app's name (a row in the Accessibility list, an app list, a battery list) is
 * fine. A screen is blocked only when it is *about* NoXXX and can disable it:
 *  1. its title names the app (NoXXX's own Accessibility page, "Stop NoXXX?" dialogs),
 *  2. a toggle is labelled with the app's name ("Use NoXXX"),
 *  3. the name appears together with an exact action button that stops, clears or removes an app
 *     (App info, its Storage page, device-admin deactivation),
 *  4. it is the package installer showing the name (the uninstall prompt),
 *  5. an editable field holds the Private DNS hostname (the Private DNS dialog).
 */
object TamperRules {
    /** Exact button labels (lowercase) that stop, clear or remove an app. */
    val DANGEROUS_ACTIONS = setOf(
        "force stop", "uninstall", "uninstall updates", "disable", "clear storage", "clear data",
        "deactivate", "deactivate this device admin app", "stop", "turn off",
    )

    val INSTALLER_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
    )

    /** Which rule above marked a screen as able to switch NoXXX off. Logged to explain each block. */
    enum class Reason(val description: String) {
        TITLE("Screen title names NoXXX"),
        TOGGLE("A switch is labelled with NoXXX's name"),
        DNS_FIELD("Private DNS hostname field"),
        ACTION_BUTTON("Stop / uninstall / clear button on a page about NoXXX"),
        INSTALLER("Uninstall prompt for NoXXX"),
    }

    fun isSensitive(nodes: List<NodeFacts>, windowTitle: String?, appLabel: String, dnsHost: String?, pkg: String) =
        reason(nodes, windowTitle, appLabel, dnsHost, pkg) != null

    fun reason(nodes: List<NodeFacts>, windowTitle: String?, appLabel: String, dnsHost: String?, pkg: String): Reason? {
        val scan = Scan(windowTitle, appLabel, dnsHost, pkg)
        if (!scan.sensitive) nodes.any(scan::offer)
        return scan.reason
    }

    fun mentions(text: CharSequence?, appLabel: String) = text?.toString()?.contains(appLabel, ignoreCase = true) == true

    /** Takes nodes one at a time so a screen walk can stop at the first node that settles it. */
    class Scan(windowTitle: String?, appLabel: String, dnsHost: String?, private val pkg: String) {
        private val label = appLabel.lowercase()
        private val host = dnsHost?.lowercase()
        private var labelSeen = false
        private var dangerousAction = false

        var reason: Reason? = Reason.TITLE.takeIf { windowTitle?.lowercase()?.contains(label) == true }
            private set
        val sensitive get() = reason != null

        fun offer(n: NodeFacts): Boolean {
            if (sensitive) return true
            val text = n.text?.lowercase()
            val desc = n.description?.lowercase()
            if (text?.contains(label) == true || desc?.contains(label) == true) {
                labelSeen = true
                if (n.checkable) reason = Reason.TOGGLE
            }
            if (reason == null && host != null && n.editable && text?.contains(host) == true) reason = Reason.DNS_FIELD
            if (text?.trim() in DANGEROUS_ACTIONS || desc?.trim() in DANGEROUS_ACTIONS) dangerousAction = true
            if (reason == null && labelSeen) {
                if (pkg in INSTALLER_PACKAGES) reason = Reason.INSTALLER
                else if (dangerousAction) reason = Reason.ACTION_BUTTON
            }
            return sensitive
        }
    }
}

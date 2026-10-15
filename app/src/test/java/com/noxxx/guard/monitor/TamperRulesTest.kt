package com.noxxx.guard.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperRulesTest {
    private val settings = "com.android.settings"
    private fun t(s: String) = NodeFacts(text = s, description = null)

    private fun sensitive(nodes: List<NodeFacts>, title: String? = null, pkg: String = settings, host: String? = null) =
        TamperRules.isSensitive(nodes, title, "NoXXX", host, pkg)

    @Test fun accessibilityListWithNoXXXRowIsAllowed() {
        val list = listOf(t("Accessibility"), t("Downloaded apps"), t("NoXXX"), t("On"), t("Other Reader"), t("Off"),
            t("Remove animations"), t("Color correction"))
        assertFalse(sensitive(list, title = "Accessibility"))
    }

    @Test fun appListsAreAllowed() {
        assertFalse(sensitive(listOf(t("All apps"), t("Chrome"), t("NoXXX"), t("12 MB"), t("Messages")), title = "All apps"))
    }

    @Test fun noXXXAccessibilityPageIsBlockedByTitle() {
        assertTrue(sensitive(listOf(t("Use NoXXX"), t("NoXXX shortcut")), title = "NoXXX"))
    }

    @Test fun toggleLabelledWithNameIsBlocked() {
        assertTrue(sensitive(listOf(NodeFacts("Use NoXXX", null, checkable = true)), title = "Settings"))
    }

    @Test fun noXXXAppInfoIsBlocked() {
        assertTrue(sensitive(listOf(t("App info"), t("NoXXX"), t("Open"), t("Uninstall"), t("Force stop")), title = "App info"))
    }

    @Test fun noXXXStoragePageIsBlocked() {
        assertTrue(sensitive(listOf(t("Storage & cache"), t("NoXXX"), t("Clear storage"), t("Clear cache"))))
    }

    @Test fun otherAppsInfoIsAllowed() {
        assertFalse(sensitive(listOf(t("App info"), t("Chrome"), t("Uninstall"), t("Force stop")), title = "App info"))
    }

    @Test fun stopDialogIsBlocked() {
        assertTrue(sensitive(listOf(t("Stop NoXXX?"), t("Cancel"), t("Stop")), pkg = settings))
    }

    @Test fun dangerousWordsInsideLongerTextDontCount() {
        assertFalse(sensitive(listOf(t("NoXXX"), t("Disable animations"), t("Stop playback when screen turns off"))))
    }

    @Test fun uninstallPromptIsBlocked() {
        assertTrue(sensitive(listOf(t("NoXXX"), t("Do you want to uninstall this app?"), t("OK")), pkg = "com.google.android.packageinstaller"))
    }

    @Test fun privateDnsDialogIsBlocked() {
        val dialog = listOf(t("Private DNS"), NodeFacts("family-filter-dns.cleanbrowsing.org", null, editable = true))
        assertTrue(sensitive(dialog, host = "family-filter-dns.cleanbrowsing.org"))
    }

    @Test fun scanSettlesAtFirstDecidingNode() {
        val scan = TamperRules.Scan("App info", "NoXXX", null, settings)
        assertFalse(scan.offer(t("NoXXX")))
        assertTrue(scan.offer(t("Force stop")))
    }

    @Test fun scanWithTitleIsSensitiveBeforeAnyNode() {
        assertTrue(TamperRules.Scan("NoXXX", "NoXXX", null, settings).sensitive)
    }

    @Test fun mentionsIgnoresCase() {
        assertTrue(TamperRules.mentions("noxxx, On", "NoXXX"))
        assertFalse(TamperRules.mentions("Chrome", "NoXXX"))
        assertFalse(TamperRules.mentions(null, "NoXXX"))
    }

    @Test fun networkPageShowingHostAsSummaryIsAllowed() {
        val page = listOf(t("Network & internet"), t("Private DNS"), t("family-filter-dns.cleanbrowsing.org"))
        assertFalse(sensitive(page, host = "family-filter-dns.cleanbrowsing.org"))
    }
}

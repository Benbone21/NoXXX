package com.noxxx.guard.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun deviceAdminActivationDialogIsAllowedDuringGrace() {
        val samsung = "Device admin app NoXXX Dev Prevents NoXXX from being uninstalled. Activating this admin app will allow NoXXX Dev to perform the following actions:"
        assertTrue(TamperRules.graceAllows("DeviceAdminAdd", adminGrace = true, "androidx.appcompat.app.AlertDialog", samsung))
        assertTrue(TamperRules.graceAllows("DeviceAdminAdd", adminGrace = true, "com.android.settings.DeviceAdminAdd", null))
        assertFalse("deactivation stays guarded",
            TamperRules.graceAllows("DeviceAdminAdd", adminGrace = true, "androidx.appcompat.app.AlertDialog", "Deactivating this admin app will stop NoXXX"))
        assertFalse("text match only applies to the device admin grace",
            TamperRules.graceAllows("RequestIgnoreBatteryOptimizations", adminGrace = false, "androidx.appcompat.app.AlertDialog", samsung))
    }

    private val samsungTitle = "Device admin app NoXXX Dev Prevents NoXXX from being uninstalled without completing the " +
        "cool-off first. Activating this admin app will allow NoXXX Dev to perform the following actions:"
    private val samsungDialog = listOf(t("Device admin app"), t("NoXXX Dev"),
        t("Prevents NoXXX from being uninstalled without completing the cool-off first."),
        t("Activating this admin app will allow NoXXX Dev to perform the following actions:"), t("Cancel"), t("Activate"))
    private val dialogClass = "androidx.appcompat.app.AlertDialog"

    private fun duringAdminGrace(nodes: List<NodeFacts>, title: String?, cls: String? = dialogClass, host: String? = null, pkg: String = settings) =
        TamperRules.reasonDuringAdminGrace(nodes, title, cls, "NoXXX", host, pkg)

    @Test fun samsungActivationDialogIsAllowedDuringAdminGrace() {
        val v = duringAdminGrace(samsungDialog, samsungTitle)
        assertTrue(v.activation)
        assertNull(v.reason)
        assertEquals("same screen is blocked without the grace", TamperRules.Reason.TITLE,
            TamperRules.reason(samsungDialog, samsungTitle, "NoXXX", null, settings))
    }

    @Test fun activationIsJudgedByScreenContentWhenTitleOrClassIsStale() {
        // A later event replaced the title ("Device admin app") and a keyboard/system window the class.
        assertNull(duringAdminGrace(samsungDialog, "Device admin app NoXXX Dev", cls = "android.inputmethodservice.SoftInputWindow").reason)
        assertNull(duringAdminGrace(samsungDialog, null, cls = null).reason)
    }

    @Test fun activationFallsBackToTitleWhileScreenIsStillLoading() {
        assertNull(duringAdminGrace(emptyList(), samsungTitle).reason)
        assertNull(duringAdminGrace(emptyList(), "NoXXX", cls = "com.samsung.android.settings.applications.specialaccess.SecDeviceAdminAdd").reason)
        assertEquals(TamperRules.Reason.TITLE, duringAdminGrace(emptyList(), "NoXXX", cls = dialogClass).reason)
    }

    @Test fun deactivationStaysBlockedDuringAdminGrace() {
        val aosp = listOf(t("Device admin app"), t("NoXXX"),
            t("This admin app is active and allows the app NoXXX to perform the following operations:"),
            t("Deactivate this device admin app"), t("Cancel"))
        val samsung = listOf(t("NoXXX Dev"), t("Deactivating this admin app will stop NoXXX Dev from performing the following actions:"),
            t("Cancel"), t("Deactivate"))
        for (screen in listOf(aosp, samsung)) {
            val v = duringAdminGrace(screen, "Device admin app NoXXX", cls = "com.android.settings.DeviceAdminAdd")
            assertFalse(v.activation)
            assertEquals(TamperRules.Reason.TITLE, v.reason)
        }
        // Even if a stale title still says "activating", the screen's own Deactivate button wins.
        assertFalse(duringAdminGrace(samsung, samsungTitle).activation)
        assertEquals(TamperRules.Reason.ACTION_BUTTON, duringAdminGrace(samsung, null).reason)
    }

    @Test fun otherNoXXXScreensStayBlockedDuringAdminGrace() {
        val appInfo = listOf(t("App info"), t("NoXXX"), t("Open"), t("Uninstall"), t("Force stop"))
        assertFalse(duringAdminGrace(appInfo, samsungTitle).activation)
        assertEquals(TamperRules.Reason.ACTION_BUTTON, duringAdminGrace(appInfo, "App info").reason)
        val a11y = listOf(t("Use NoXXX"), t("NoXXX shortcut"))
        assertEquals("stale activation title can't open NoXXX's Accessibility page",
            TamperRules.Reason.TITLE, duringAdminGrace(a11y, "NoXXX", cls = "com.android.settings.DeviceAdminAdd").reason)
        assertNotNull(duringAdminGrace(listOf(NodeFacts("Use NoXXX", null, checkable = true)), samsungTitle).reason)
        val uninstall = listOf(t("NoXXX"), t("Do you want to uninstall this app?"), t("OK"))
        assertEquals(TamperRules.Reason.INSTALLER,
            duringAdminGrace(uninstall, null, pkg = "com.google.android.packageinstaller").reason)
        val dns = listOf(t("Private DNS"), NodeFacts("dns.example", null, editable = true))
        assertEquals(TamperRules.Reason.DNS_FIELD, duringAdminGrace(dns, null, host = "dns.example").reason)
    }

    @Test fun reasonNamesTheRuleThatMatched() {
        assertEquals(TamperRules.Reason.TITLE, TamperRules.reason(listOf(t("Use NoXXX")), "NoXXX", "NoXXX", null, settings))
        assertEquals(TamperRules.Reason.TOGGLE,
            TamperRules.reason(listOf(NodeFacts("Use NoXXX", null, checkable = true)), "Settings", "NoXXX", null, settings))
        assertEquals(TamperRules.Reason.ACTION_BUTTON,
            TamperRules.reason(listOf(t("NoXXX"), t("Force stop")), "App info", "NoXXX", null, settings))
        assertNull(TamperRules.reason(listOf(t("All apps"), t("NoXXX")), "All apps", "NoXXX", null, settings))
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

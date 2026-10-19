package com.noxxx.guard.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureScreenExemptionsTest {
    @Test fun listedSecurityAppsAreExempt() {
        assertTrue(SecureScreenExemptions.isExempt("com.x8bit.bitwarden", isSystemApp = false, isBrowser = false))
    }

    @Test fun preinstalledSystemAppsAreExempt() {
        // Samsung's fingerprint/face setup and the Play Store's payment screens block screenshots.
        assertTrue(SecureScreenExemptions.isExempt("com.samsung.android.biometrics.app.setting", isSystemApp = true, isBrowser = false))
        assertTrue(SecureScreenExemptions.isExempt("com.android.vending", isSystemApp = true, isBrowser = false))
    }

    @Test fun preinstalledBrowsersStillBlockPrivateTabs() {
        assertFalse(SecureScreenExemptions.isExempt("com.sec.android.app.sbrowser", isSystemApp = true, isBrowser = true))
    }

    @Test fun twoFactorAndVpnAppsAreExempt() {
        assertTrue(SecureScreenExemptions.isExempt("com.duosecurity.duomobile", isSystemApp = false, isBrowser = false))
        assertTrue(SecureScreenExemptions.isExempt("com.zaneschepke.wireguardautotunnel", isSystemApp = false, isBrowser = false))
        assertTrue(SecureScreenExemptions.isExempt("com.azure.authenticator", isSystemApp = false, isBrowser = false))
    }

    @Test fun appsTheUserAllowedAreExempt() {
        assertTrue(SecureScreenExemptions.isExempt("com.example.notes", false, false, userAllowed = setOf("com.example.notes")))
    }

    @Test fun ordinaryInstalledAppsAreNotExempt() {
        assertFalse(SecureScreenExemptions.isExempt("com.reddit.frontpage", isSystemApp = false, isBrowser = false))
    }
}

package com.noxxx.guard.monitor

import com.noxxx.guard.policy.Policy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoredAppsTest {
    @Test fun listedAppIsIgnored() {
        assertTrue(IgnoredApps.isIgnored("org.thoughtcrime.securesms", setOf("org.thoughtcrime.securesms"), isBrowser = false))
        assertFalse(IgnoredApps.isIgnored("com.instagram.android", setOf("org.thoughtcrime.securesms"), isBrowser = false))
    }

    @Test fun browsersAreNeverIgnored() {
        assertFalse(IgnoredApps.isIgnored("com.android.chrome", setOf("com.android.chrome"), isBrowser = true))
    }

    @Test fun guardedSystemScreensAreNeverIgnored() {
        TamperGuard.GUARDED_PACKAGES.forEach { pkg ->
            assertFalse(pkg, IgnoredApps.isIgnored(pkg, setOf(pkg), isBrowser = false))
        }
    }

    @Test fun ignoringAnAppIsALoosening() {
        val old = Policy()
        assertFalse(old.copy(ignoredApps = setOf("a.b")).isAtLeastAsStrictAs(old))
        assertTrue(old.isAtLeastAsStrictAs(old.copy(ignoredApps = setOf("a.b"))))
    }
}

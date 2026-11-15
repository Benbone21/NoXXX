package com.noxxx.guard.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommAppsTest {
    @Test fun phoneCallScreensAreSkippedLikeOtherCallApps() {
        // Video calls in the phone app show your own camera, which set off false alarms.
        assertTrue(CommApps.isCommApp("com.samsung.android.incallui"))
        assertTrue(CommApps.isCommApp("com.google.android.dialer"))
    }

    @Test fun feedAppsStayScanned() {
        assertFalse(CommApps.isCommApp("com.instagram.android"))
        assertFalse(CommApps.isCommApp("com.facebook.katana"))
    }
}

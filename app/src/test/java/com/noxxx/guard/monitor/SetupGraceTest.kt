package com.noxxx.guard.monitor

import com.noxxx.guard.monitor.TamperGuard.Grace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupGraceTest {
    @Test fun adminGraceLastsLongEnoughToReadTheScreen() {
        val g = SetupGrace()
        g.grant(Grace.DEVICE_ADMIN, now = 0)
        assertEquals(Grace.DEVICE_ADMIN, g.active(90_000))
        assertNull(g.active(Grace.DEVICE_ADMIN.durationMs))
    }

    @Test fun adminGraceIsKeptAliveWhileTheActivationScreenIsInFrontButCapped() {
        val g = SetupGrace()
        g.grant(Grace.DEVICE_ADMIN, now = 0)
        var t = 0L
        while (t < 590_000) { t += 60_000; g.extend(Grace.DEVICE_ADMIN, t) }
        assertEquals(Grace.DEVICE_ADMIN, g.active(599_000))
        assertNull("never beyond the maximum", g.active(Grace.DEVICE_ADMIN.maxMs))
    }

    @Test fun otherGracesDontExtend() {
        val g = SetupGrace()
        g.grant(Grace.PRIVATE_DNS, now = 0)
        g.extend(Grace.PRIVATE_DNS, 40_000)
        g.extend(Grace.DEVICE_ADMIN, 40_000)
        assertNull(g.active(45_000))
    }

    @Test fun expiredGraceCantBeRevived() {
        val g = SetupGrace()
        g.grant(Grace.DEVICE_ADMIN, now = 0)
        g.extend(Grace.DEVICE_ADMIN, Grace.DEVICE_ADMIN.durationMs + 1)
        assertNull(g.active(Grace.DEVICE_ADMIN.durationMs + 2))
    }

    @Test fun returningToTheAppKeepsOnlyTheAdminGrace() {
        val g = SetupGrace()
        g.grant(Grace.DEVICE_ADMIN, now = 0)
        g.end(keep = Grace.DEVICE_ADMIN)
        assertEquals(Grace.DEVICE_ADMIN, g.active(1_000))
        g.end()
        assertNull(g.active(1_000))

        g.grant(Grace.FULL, now = 0)
        g.end(keep = Grace.DEVICE_ADMIN)
        assertNull(g.active(1_000))
    }
}

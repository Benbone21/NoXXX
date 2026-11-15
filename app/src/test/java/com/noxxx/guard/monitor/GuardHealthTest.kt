package com.noxxx.guard.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardHealthTest {
    @Test fun switchedOnAndRunningIsOn() {
        assertEquals(GuardHealth.ON, GuardHealth.of(enabled = true, running = true))
    }

    @Test fun switchedOnButNotRunningIsStalled() {
        // Android's "This service is malfunctioning": the switch is on, but nothing is protecting the phone.
        assertEquals(GuardHealth.STALLED, GuardHealth.of(enabled = true, running = false))
    }

    @Test fun switchedOffIsOffEvenWhileStillShuttingDown() {
        assertEquals(GuardHealth.OFF, GuardHealth.of(enabled = false, running = false))
        assertEquals(GuardHealth.OFF, GuardHealth.of(enabled = false, running = true))
    }
}

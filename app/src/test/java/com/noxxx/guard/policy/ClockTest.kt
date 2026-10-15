package com.noxxx.guard.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test fun sameBootCreditsMonotonicDelta() {
        var (c, d) = Clock.tick(ClockState(), ClockReading(1, 1_000))
        assertEquals(0, d)
        Clock.tick(c, ClockReading(1, 61_000)).let { c = it.first; d = it.second }
        assertEquals(60_000, d)
    }

    @Test fun rebootCreditsUptimeButHoldsGapUntilNetworkTime() {
        var c = Clock.tick(ClockState(), ClockReading(1, 1_000)).first
        c = Clock.onNetworkTime(c, networkMs = 1_000_000, elapsedAtFetchMs = 1_000).first
        c = Clock.tick(c, ClockReading(1, 11_000)).first // trusted wall now 1_010_000

        // Phone is off for 100 s, then boots; first tick 5 s after boot.
        val (afterBoot, d) = Clock.tick(c, ClockReading(2, 5_000))
        assertEquals(5_000, d)

        // Network fix 2 s later: boot happened at 1_010_000 + 100_000.
        val (_, gap) = Clock.onNetworkTime(afterBoot, networkMs = 1_117_000, elapsedAtFetchMs = 7_000)
        assertEquals(100_000, gap)
    }

    @Test fun noGapCreditWithoutTrustedTimeBeforeReboot() {
        var c = Clock.tick(ClockState(), ClockReading(1, 1_000)).first
        c = Clock.tick(c, ClockReading(1, 2_000)).first
        val afterBoot = Clock.tick(c, ClockReading(2, 1_000)).first
        val (_, gap) = Clock.onNetworkTime(afterBoot, networkMs = 9_999_999_999, elapsedAtFetchMs = 1_000)
        assertEquals(0, gap)
    }
}

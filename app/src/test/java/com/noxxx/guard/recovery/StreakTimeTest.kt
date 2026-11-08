package com.noxxx.guard.recovery

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StreakTimeTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun ms(t: LocalDateTime) = t.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun breaksTheStreakIntoAClockAndWholeUnits() {
        val start = LocalDateTime.of(2026, 9, 1, 20, 15, 0)
        val now = start.plusDays(68).plusHours(5).plusMinutes(7).plusSeconds(42)
        val t = StreakTime.between(ms(start), ms(now), utc)
        assertEquals(68, t.days)
        assertEquals(5, t.hours)
        assertEquals(7, t.minutes)
        assertEquals(42, t.seconds)
        assertEquals(9, t.weeks)
        assertEquals(5, t.extraDays)
        assertEquals(2, t.months)
        assertEquals(68 * 24 + 5L, t.totalHours)
    }

    @Test fun aClockSetBackwardsNeverGoesNegative() {
        val t = StreakTime.between(1_000_000, 0, utc)
        assertEquals(0, t.days)
        assertEquals(0, t.seconds)
        assertEquals(0, t.months)
    }
}

package com.noxxx.guard.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    @Test fun durationsAlwaysShowHoursAndMinutes() {
        assertEquals("45m", formatDuration(45 * min))
        assertEquals("3h 5m", formatDuration(3 * hour + 5 * min))
        assertEquals("3h 0m", formatDuration(3 * hour))
        assertEquals("1d 2h 15m", formatDuration(day + 2 * hour + 15 * min))
        assertEquals("6d 0h 0m", formatDuration(6 * day))
    }

    @Test fun countdownsUseLabelledUnits() {
        assertEquals("14m 32s", formatCountdown(14 * min + 32_000))
        assertEquals("45s", formatCountdown(45_000))
        assertEquals("1h 2m 3s", formatCountdown(hour + 2 * min + 3_000))
    }
}

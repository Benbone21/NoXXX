package com.noxxx.guard.recovery

import java.time.Instant
import java.time.Period
import java.time.ZoneId

/** A streak broken down for display: an exact running clock plus whole weeks, months and hours. */
data class StreakTime(
    val days: Long,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val weeks: Long,
    val extraDays: Int,
    val months: Int,
    val totalHours: Long,
) {
    companion object {
        fun between(startMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): StreakTime {
            val ms = (nowMs - startMs).coerceAtLeast(0)
            val totalSeconds = ms / 1000
            val days = totalSeconds / 86_400
            val start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
            val now = Instant.ofEpochMilli(maxOf(nowMs, startMs)).atZone(zone).toLocalDate()
            return StreakTime(
                days = days,
                hours = ((totalSeconds % 86_400) / 3600).toInt(),
                minutes = ((totalSeconds % 3600) / 60).toInt(),
                seconds = (totalSeconds % 60).toInt(),
                weeks = days / 7,
                extraDays = (days % 7).toInt(),
                months = Period.between(start, now).toTotalMonths().toInt(),
                totalHours = totalSeconds / 3600,
            )
        }
    }
}

package com.noxxx.guard.policy

import kotlinx.serialization.Serializable

/**
 * Tamper-resistant time accounting for the cool-off and lockdown timers.
 *
 * - While the phone is on, time is measured with the monotonic `elapsedRealtime` clock, which
 *   changing the system date/time does not affect.
 * - Time the phone spends switched off is only credited once it can be measured against network
 *   time on both sides of the reboot, so setting the clock forward and rebooting gains nothing.
 */
@Serializable
data class ClockState(
    val bootCount: Int = -1,
    val lastElapsedMs: Long = 0,
    /** Network time (ms since epoch) observed at [anchorElapsedMs] during the current boot. */
    val anchorNetworkMs: Long? = null,
    val anchorElapsedMs: Long = 0,
    /** Trusted (network-anchored) wall time at the last checkpoint of the current boot. */
    val lastTrustedWallMs: Long? = null,
    /** After a reboot: the last trusted wall time from before it, waiting for a network fix. */
    val gapStartTrustedWallMs: Long? = null,
) {
    val needsNetworkTime get() = anchorNetworkMs == null || gapStartTrustedWallMs != null
}

data class ClockReading(val bootCount: Int, val elapsedMs: Long)

object Clock {
    /** Returns the updated clock and how many ms of real time to credit to the timers. */
    fun tick(clock: ClockState, now: ClockReading): Pair<ClockState, Long> {
        val sameBoot = clock.bootCount == now.bootCount && now.elapsedMs >= clock.lastElapsedMs
        if (clock.bootCount == -1) {
            return clock.copy(bootCount = now.bootCount, lastElapsedMs = now.elapsedMs) to 0L
        }
        if (sameBoot) {
            val delta = now.elapsedMs - clock.lastElapsedMs
            val trusted = clock.anchorNetworkMs?.let { it + (now.elapsedMs - clock.anchorElapsedMs) }
            return clock.copy(lastElapsedMs = now.elapsedMs, lastTrustedWallMs = trusted) to delta
        }
        // New boot: the time since boot is certainly real; the powered-off gap is credited later.
        val rebooted = ClockState(
            bootCount = now.bootCount,
            lastElapsedMs = now.elapsedMs,
            gapStartTrustedWallMs = clock.lastTrustedWallMs ?: clock.gapStartTrustedWallMs,
        )
        return rebooted to now.elapsedMs
    }

    /** Records a network time fix. Returns the updated clock and any verified powered-off gap to credit. */
    fun onNetworkTime(clock: ClockState, networkMs: Long, elapsedAtFetchMs: Long): Pair<ClockState, Long> {
        var credit = 0L
        clock.gapStartTrustedWallMs?.let { gapStart ->
            val bootStartTrusted = networkMs - elapsedAtFetchMs
            credit = (bootStartTrusted - gapStart).coerceAtLeast(0)
        }
        val updated = clock.copy(
            anchorNetworkMs = networkMs,
            anchorElapsedMs = elapsedAtFetchMs,
            lastTrustedWallMs = networkMs + (clock.lastElapsedMs - elapsedAtFetchMs),
            gapStartTrustedWallMs = null,
        )
        return updated to credit
    }
}

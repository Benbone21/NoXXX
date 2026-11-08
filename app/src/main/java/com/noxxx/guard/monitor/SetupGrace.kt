package com.noxxx.guard.monitor

/**
 * The pass behind [TamperGuard.Grace]: which kind is active and until when. Pure (the caller passes
 * the clock) so it can be unit-tested.
 */
class SetupGrace {
    private var kind: TamperGuard.Grace? = null
    private var until = 0L
    private var hardUntil = 0L

    @Synchronized
    fun grant(kind: TamperGuard.Grace, now: Long, ms: Long = kind.durationMs) {
        this.kind = kind
        until = now + ms
        hardUntil = now + maxOf(ms, kind.maxMs)
    }

    @Synchronized
    fun active(now: Long): TamperGuard.Grace? = kind.takeIf { now < until }

    /**
     * Keeps the pass alive while the screen it was granted for is still in front, so reading it
     * slowly doesn't get it closed. Never beyond the kind's maximum, and only for the same kind.
     */
    @Synchronized
    fun extend(kind: TamperGuard.Grace, now: Long) {
        if (active(now) != kind) return
        until = minOf(hardUntil, maxOf(until, now + kind.durationMs))
    }

    /** Ends the pass, unless it is of kind [keep]. */
    @Synchronized
    fun end(keep: TamperGuard.Grace? = null) {
        if (keep != null && kind == keep) return
        kind = null
        until = 0
        hardUntil = 0
    }
}

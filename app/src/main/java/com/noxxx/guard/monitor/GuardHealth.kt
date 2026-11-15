package com.noxxx.guard.monitor

/** Whether the guard service is really protecting the phone, not just switched on in Accessibility. */
enum class GuardHealth {
    /** Switched on and running. */
    ON,

    /**
     * Switched on, but Android isn't running it: it crashed or was killed and not restarted (common on
     * Xiaomi phones without Autostart). Android's Accessibility page then says "This service is
     * malfunctioning" or "Not working".
     */
    STALLED,

    /** Switched off. */
    OFF;

    companion object {
        fun of(enabled: Boolean, running: Boolean) = when {
            !enabled -> OFF
            running -> ON
            else -> STALLED
        }
    }
}

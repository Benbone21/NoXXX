package com.noxxx.guard.recovery

/**
 * Streak milestones. Wording adapted from Quitter's pornography journey
 * (https://github.com/brandonp2412/Quitter, MIT License, Copyright (c) 2025 Quitter).
 */
data class Milestone(val days: Int, val title: String, val description: String)

object Milestones {
    val ALL = listOf(
        Milestone(1, "One day in control", "The first day is mostly about riding out the urge and noticing what sets it off. That's useful to know."),
        Milestone(3, "Three days, learning urges", "Urges, mood changes or poor sleep can happen. Three days is enough to start spotting your own triggers."),
        Milestone(7, "A week of choices", "You're learning when urges show up and what's behind them: a map of your own triggers."),
        Milestone(14, "Two weeks, know the triggers", "The habit is losing its grip, and the situations that fed it have less power."),
        Milestone(30, "A month in charge", "The old reflex is fading, and real connection is getting more of your attention."),
        Milestone(90, "Three months, change that sticks", "The habit that used to run in the background is a lot quieter now."),
        Milestone(180, "Half a year", "Your mind has had real time to reset and heal."),
        Milestone(365, "One year", "Late nights and boredom no longer have to dictate the habit."),
        Milestone(730, "Two years", "The habit is gone, and the time it used to take is yours now."),
        Milestone(1095, "Three years", "Your attention has had a long stretch to point somewhere real."),
        Milestone(1460, "Four years, your terms", "The old pattern is well behind you, and the new one is the default."),
        Milestone(1825, "Five years", "Your attention now works for you instead of against you."),
    )

    /** The most recent milestone reached after [days] clean days, or null before the first. */
    fun reached(days: Long): Milestone? = ALL.lastOrNull { it.days <= days }

    /** The next milestone to aim for, or null after the last one. */
    fun next(days: Long): Milestone? = ALL.firstOrNull { it.days > days }

    /** Progress from the last milestone (or day 0) toward [next], from 0 to 1. */
    fun progress(days: Long): Float {
        val next = next(days) ?: return 1f
        val from = reached(days)?.days ?: 0
        return ((days - from).toFloat() / (next.days - from)).coerceIn(0f, 1f)
    }
}

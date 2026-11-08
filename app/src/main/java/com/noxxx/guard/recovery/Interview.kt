package com.noxxx.guard.recovery

import com.noxxx.guard.policy.Policy
import com.noxxx.guard.policy.Sensitivity
import kotlinx.serialization.Serializable

enum class Duration(val label: String) {
    RECENT("Under a year"), FEW_YEARS("1 to 3 years"), YEARS("3 to 10 years"), DECADE("More than 10 years")
}

enum class Frequency(val label: String) {
    RARELY("Less than once a week"), WEEKLY("About once a week"), OFTEN("A few times a week"), DAILY("Every day or more")
}

enum class Attempts(val label: String) {
    FIRST("This is my first real try"), QUICK_SLIPS("I've tried, but slip within days"),
    LONG_THEN_RELAPSE("I've had long streaks, then relapsed"),
}

/** Setup interview answers. Kept on the phone only. */
@Serializable
data class Answers(
    val duration: Duration = Duration.RECENT,
    val frequency: Frequency = Frequency.WEEKLY,
    val attempts: Attempts = Attempts.FIRST,
    /** From [Interview.HARD_TIMES]. */
    val hardTimes: Set<String> = emptySet(),
    /** From [Interview.SOURCES]. */
    val sources: Set<String> = emptySet(),
    val hasSupport: Boolean = false,
    /** From [Interview.FAITH]. */
    val faith: Set<String> = setOf(Interview.PRAYER, Interview.SCRIPTURES),
)

data class Recommendation(
    val goals: List<Goal>,
    val lockdownDays: Int,
    val coolOffMinutes: Int,
    val sensitivity: Sensitivity,
    /** Installed social apps to pre-check on the block list. */
    val blockApps: Set<String>,
    val why: List<String>,
)

object Interview {
    const val LATE = "Late at night"
    const val ALONE = "When I'm alone"
    const val BORED = "Bored"
    const val STRESSED = "Stressed or anxious"
    const val LONELY = "Lonely"
    const val TIRED = "Tired"
    val HARD_TIMES = listOf(LATE, ALONE, BORED, STRESSED, LONELY, TIRED)

    const val SEARCH = "Searching in a browser"
    const val SOCIAL = "Social media feeds"
    const val SITES = "Particular sites or apps"
    const val GAMES = "Games or anime"
    val SOURCES = listOf(SEARCH, SOCIAL, SITES, GAMES)

    const val PRAYER = "Prayer"
    const val SCRIPTURES = "Scripture study"
    const val COME_FOLLOW_ME = "Come, Follow Me"
    const val JOURNAL = "Journal"
    const val SERVICE = "Serving others"
    val FAITH = listOf(PRAYER, SCRIPTURES, COME_FOLLOW_ME, JOURNAL, SERVICE)

    /** Social apps that commonly lead to explicit content. */
    val SOCIAL_APPS = setOf(
        "com.reddit.frontpage", "com.instagram.android", "com.twitter.android", "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill", "com.snapchat.android", "com.tumblr", "com.pinterest",
    )

    fun recommend(a: Answers, installed: Set<String>): Recommendation {
        val why = mutableListOf<String>()

        val baseLockdown = when (a.frequency) {
            Frequency.RARELY -> 0
            Frequency.WEEKLY -> 7
            Frequency.OFTEN -> 14
            Frequency.DAILY -> 30
        }
        val lockdown = if (a.attempts == Attempts.LONG_THEN_RELAPSE) maxOf(baseLockdown, 14) else baseLockdown
        if (lockdown > 0) why += "A $lockdown-day lockdown gets you through the hardest early stretch with nothing to loosen."
        if (a.attempts == Attempts.LONG_THEN_RELAPSE) why += "Relapses after long streaks often come when guard is let down, so settings stay strict."

        val coolOff = when (a.frequency) {
            Frequency.RARELY -> 24 * 60
            Frequency.WEEKLY -> 48 * 60
            Frequency.OFTEN -> 72 * 60
            Frequency.DAILY -> 7 * 24 * 60
        }
        why += "A ${coolOff / 60 / 24}-day cool-off is longer than an urge lasts."

        val blockApps = if (SOCIAL in a.sources) SOCIAL_APPS intersect installed else emptySet()
        if (blockApps.isNotEmpty()) why += "Social apps you named as a starting point are pre-checked to block."
        if (SEARCH in a.sources) why += "Search blocking and private-tab blocking stay on."

        val goals = buildList {
            if (PRAYER in a.faith) { add(Goal("pray-morning", "Morning prayer")); add(Goal("pray-evening", "Evening prayer")) }
            if (SCRIPTURES in a.faith) add(Goal("scriptures", "Scripture study"))
            if (COME_FOLLOW_ME in a.faith) add(Goal("cfm", "Come, Follow Me study"))
            if (JOURNAL in a.faith) add(Goal("journal", "Write in my journal"))
            if (SERVICE in a.faith) add(Goal("serve", "Serve someone"))
            if (LATE in a.hardTimes || TIRED in a.hardTimes) add(Goal("phone-away", "Phone out of the bedroom at night"))
            if (ALONE in a.hardTimes || LONELY in a.hardTimes) add(Goal("reach-out", "Reach out to someone"))
            if (STRESSED in a.hardTimes || BORED in a.hardTimes) add(Goal("move", "Exercise or go for a walk"))
        }.ifEmpty { RecoveryState.DEFAULT_GOALS }

        return Recommendation(goals, lockdown, coolOff, Sensitivity.BALANCED, blockApps, why)
    }

    /** The recommended protection: everything on, with the suggested cool-off, sensitivity and apps. */
    fun applyTo(p: Policy, r: Recommendation) = p.copy(
        urlFilter = true, builtInKeywords = true, checkSearchBoxes = true, imageScan = true,
        blockPrivateBrowsing = true, blockUnsupportedBrowsers = true, tamperProtection = true,
        coolOffMinutes = r.coolOffMinutes, sensitivity = r.sensitivity, blockedApps = p.blockedApps + r.blockApps,
    )
}

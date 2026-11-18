package com.noxxx.guard.recovery

import com.noxxx.guard.policy.Policy
import com.noxxx.guard.policy.Sensitivity
import kotlinx.serialization.SerialName
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

enum class LastSlip(val label: String) {
    DAYS("In the last day or two"), WEEK("In the last week"), MONTH("In the last month"),
    LONGER("More than a month ago"), PRIVATE("I'd rather not say");

    val isRecent get() = this == DAYS || this == WEEK
}

enum class Strictness(val label: String) {
    GENTLE("Gentle: block the worst, stay out of my way"),
    FIRM("Firm: solid protection, a few hoops to loosen"),
    STRICT("Strict: make it hard to get around"),
    MAXIMUM("As locked down as possible"),
}

/** Setup interview answers. Kept on the phone only. Unanswered questions are null. */
@Serializable
data class Answers(
    val duration: Duration = Duration.RECENT,
    val frequency: Frequency = Frequency.WEEKLY,
    val attempts: Attempts = Attempts.FIRST,
    val lastSlip: LastSlip? = null,
    /** From [Interview.HARD_TIMES]. */
    val hardTimes: Set<String> = emptySet(),
    /** From [Interview.SOURCES]. */
    val sources: Set<String> = emptySet(),
    /** Who knows and supports them, from [Interview.SUPPORT]. */
    val support: Set<String> = emptySet(),
    /** The old yes/no support question. Read only to migrate; cleared once [support] is answered. */
    @SerialName("hasSupport") val legacyHasSupport: Boolean = false,
    /** From [Interview.FAITH]. */
    val faith: Set<String> = setOf(Interview.PRAYER, Interview.SCRIPTURES),
    val strictness: Strictness? = null,
    /** From [Interview.PHONE]. */
    val phone: Set<String> = emptySet(),
) {
    /** The people who know, minus "No one yet". An old "yes, I have someone" counts as family or a friend. */
    val supporters: Set<String>
        get() = when {
            support.isNotEmpty() -> support - Interview.NO_ONE
            legacyHasSupport -> setOf(Interview.FAMILY)
            else -> emptySet()
        }
}

/** How a level treats social apps. */
enum class SocialBlock { NONE, NAMED, ALL }

/** A bundle of protection settings, from lightest to most locked down. Core checks stay on at every level. */
@Serializable
enum class ProtectionLevel(
    val label: String,
    val description: String,
    val sensitivity: Sensitivity,
    val coolOffDays: Int,
    val lockdownDays: Int,
    val social: SocialBlock,
    val blockUnsupportedBrowsers: Boolean,
    val checkSearchBoxes: Boolean,
    val scanMessagingApps: Boolean,
) {
    LIGHT("Light", "Blocks the worst and stays out of your way.",
        Sensitivity.RELAXED, 1, 0, SocialBlock.NONE, false, false, false),
    STANDARD("Standard", "Solid protection, with a short wait before anything loosens.",
        Sensitivity.BALANCED, 2, 7, SocialBlock.NAMED, true, true, false),
    STRICT("Strict", "Hard to get around, with time to think before loosening.",
        Sensitivity.BALANCED, 3, 14, SocialBlock.NAMED, true, true, false),
    MAXIMUM("Maximum", "Everything on, a long wait, and social apps blocked.",
        Sensitivity.STRICT, 7, 30, SocialBlock.ALL, true, true, true);

    /** Plain summary, like "Images: balanced · Loosening waits 3 days · 14-day lockdown". */
    val summary: String
        get() = "Images: ${sensitivity.name.lowercase()} · Loosening waits $coolOffDays ${if (coolOffDays == 1) "day" else "days"} · " +
            if (lockdownDays == 0) "No lockdown" else "$lockdownDays-day lockdown"

    fun step(by: Int) = entries[(ordinal + by).coerceIn(0, entries.lastIndex)]
}

/** The settings a level writes into the policy, after phone-habit adjustments. */
@Serializable
data class LevelSettings(
    val sensitivity: Sensitivity,
    val coolOffMinutes: Int,
    val blockUnsupportedBrowsers: Boolean,
    val blockPrivateBrowsing: Boolean,
    val checkSearchBoxes: Boolean,
    val requirePrivateDns: Boolean,
    val tamperProtection: Boolean,
    val scanMessagingApps: Boolean,
    /** Installed social apps to add to the block list. */
    val blockApps: Set<String>,
)

/** A level's settings for this person, with a note for each answer that changed something. */
data class Bundle(val level: ProtectionLevel, val settings: LevelSettings, val notes: List<String>)

data class SuggestedGoal(val goal: Goal, val reason: String)

data class Recommendation(
    val level: ProtectionLevel,
    /** Points from everything but strictness; see [Interview.recommend]. */
    val points: Int,
    /** Why this level, naming the answers behind it. */
    val why: List<String>,
    val bundle: Bundle,
    /** Goals to pre-check, in library order. */
    val goals: List<SuggestedGoal>,
) {
    val lockdownDays get() = level.lockdownDays
}

/** What the user picked by hand during setup, so recomputing never undoes it. */
@Serializable
data class SetupChoices(
    /** Level picked on the level page, or null to follow the suggestion. */
    val level: ProtectionLevel? = null,
    /** Lockdown picked on the level page, or null to follow the level. */
    val lockdownDays: Int? = null,
    /** Built-in goals the user checked or unchecked; these keep their state. */
    val touchedGoals: Set<String> = emptySet(),
    /** Settings last written to the policy from a level, so they're only rewritten when the suggestion changes. */
    val applied: LevelSettings? = null,
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

    const val SPOUSE = "A spouse or partner"
    const val FAMILY = "Family or a friend"
    const val BISHOP = "My bishop or branch president"
    const val GROUP = "A recovery group (like ARP)"
    const val NO_ONE = "No one yet"
    val SUPPORT = listOf(SPOUSE, FAMILY, BISHOP, GROUP, NO_ONE)

    const val PRAYER = "Prayer"
    const val SCRIPTURES = "Scripture study"
    const val COME_FOLLOW_ME = "Come, Follow Me"
    const val JOURNAL = "Journal"
    const val SERVICE = "Serving others"
    val FAITH = listOf(PRAYER, SCRIPTURES, COME_FOLLOW_ME, JOURNAL, SERVICE)

    const val VPN = "I use a VPN most of the time"
    const val OTHER_BROWSER = "I use a browser other than Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, Vivaldi, DuckDuckGo or Mi Browser"
    const val GAMING = "Games are a big part of my phone use"
    val PHONE = listOf(VPN, OTHER_BROWSER, GAMING)

    /** Social apps that commonly lead to explicit content. */
    val SOCIAL_APPS = setOf(
        "com.reddit.frontpage", "com.instagram.android", "com.twitter.android", "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill", "com.snapchat.android", "com.tumblr", "com.pinterest",
    )

    const val MIN_GOALS = 4
    const val MAX_GOALS = 7

    /** "No one yet" can't be picked alongside a person. */
    fun toggleSupport(selected: Set<String>, option: String): Set<String> = when {
        option in selected -> selected - option
        option == NO_ONE -> setOf(NO_ONE)
        else -> selected - NO_ONE + option
    }

    /**
     * Suggests a level. Strictness sets the starting level (unanswered counts as firm). The other answers add
     * points: frequency 0–3, three or more years +1, quick slips +1 or relapse after long streaks +2, a slip in
     * the last day or two +2 or the last week +1, and +1 per place it starts (up to 2). 5–7 points move it up one
     * step, 8 or more two. Gentle stops at Standard, or Strict for daily use with a recent slip. Having someone
     * who knows eases it back one step, but never below the starting level, or Standard for frequent use.
     */
    fun recommend(a: Answers, installed: Set<String>): Recommendation {
        val why = mutableListOf<String>()
        val base = when (a.strictness) {
            Strictness.GENTLE -> ProtectionLevel.LIGHT
            Strictness.FIRM, null -> ProtectionLevel.STANDARD
            Strictness.STRICT -> ProtectionLevel.STRICT
            Strictness.MAXIMUM -> ProtectionLevel.MAXIMUM
        }
        when (a.strictness) {
            Strictness.GENTLE -> why += "You asked for gentle protection, so it starts light."
            Strictness.FIRM -> why += "You asked for solid protection with a few hoops to loosen."
            Strictness.STRICT -> why += "You asked to make it hard to get around."
            Strictness.MAXIMUM -> why += "You asked for as locked down as possible."
            null -> Unit
        }

        var points = 0
        val pointWhy = mutableListOf<String>()
        fun add(n: Int, reason: String) {
            if (n > 0) { points += n; pointWhy += reason }
        }
        add(a.frequency.ordinal, when (a.frequency) {
            Frequency.DAILY -> "It's been every day lately."
            Frequency.OFTEN -> "It's been a few times a week."
            else -> "It's been about once a week."
        })
        add(if (a.duration >= Duration.YEARS) 1 else 0, "It's been a struggle for years, so the habit runs deep.")
        when (a.attempts) {
            Attempts.QUICK_SLIPS -> add(1, "Past tries slipped within days, so a little more friction helps.")
            Attempts.LONG_THEN_RELAPSE -> add(2, "Relapses after long streaks often come when guard is let down, so it stays firmer.")
            Attempts.FIRST -> Unit
        }
        when (a.lastSlip) {
            LastSlip.DAYS -> add(2, "Your last slip was in the last day or two, and the first days after are often the hardest.")
            LastSlip.WEEK -> add(1, "Your last slip was in the last week.")
            else -> Unit
        }
        add(a.sources.size.coerceAtMost(2),
            if (a.sources.size == 1) "You named a place it usually starts." else "You named ${a.sources.size} places it usually starts.")

        val push = when {
            points >= 8 -> 2
            points >= 5 -> 1
            else -> 0
        }
        var level = base.step(push)
        if (base != ProtectionLevel.MAXIMUM) {
            if (push > 0) {
                why += pointWhy
                why += "Together, these move it up ${if (push == 1) "one step" else "two steps"}."
            } else if (a.strictness != null) {
                why += "Your other answers don't call for more than that."
            }
        }

        if (a.strictness == Strictness.GENTLE) {
            val cap = if (a.frequency == Frequency.DAILY && a.lastSlip?.isRecent == true) ProtectionLevel.STRICT else ProtectionLevel.STANDARD
            if (level > cap) {
                level = cap
                why += "You asked for gentle, so it stops at ${cap.label}."
            }
        }

        val floor = maxOf(base, if (a.frequency >= Frequency.OFTEN) ProtectionLevel.STANDARD else ProtectionLevel.LIGHT)
        if (a.supporters.isNotEmpty() && level > floor) {
            level = level.step(-1)
            why += "Someone knows and supports you, so it eases back a step."
        }

        return Recommendation(level, points, why, bundle(level, a, installed), suggestGoals(a))
    }

    /** [level]'s settings, adjusted so they don't fight how this person uses their phone. */
    fun bundle(level: ProtectionLevel, a: Answers, installed: Set<String>): Bundle {
        val notes = mutableListOf<String>()
        val social = SOCIAL_APPS intersect installed
        val blockApps = when (level.social) {
            SocialBlock.ALL -> social
            SocialBlock.NAMED -> if (SOCIAL in a.sources) social else emptySet()
            SocialBlock.NONE -> emptySet()
        }
        when {
            blockApps.isEmpty() && SOCIAL in a.sources && social.isNotEmpty() ->
                notes += "You named social media. You can block those apps on the Apps step."
            blockApps.isEmpty() -> Unit
            SOCIAL in a.sources -> notes += "Social apps you named as a starting point are added to the block list."
            else -> notes += "${level.label} blocks the social apps on your phone."
        }

        val searchBoxes = level.checkSearchBoxes || SEARCH in a.sources
        if (!level.checkSearchBoxes && searchBoxes) notes += "You said it often starts with a search, so search boxes in apps are checked too."

        // A VPN usually sends lookups through its own servers, so the family filter can't apply while it's on, and
        // requiring it would keep pausing browsers. Breaking the phone helps no one, so it's off at every level.
        val privateDns = VPN !in a.phone
        if (!privateDns) {
            notes += "You use a VPN, which can't run alongside the family filter, so Require Private DNS is off and your " +
                "browsers won't pause. Website, search and image checks still protect you."
        }

        var unsupported = level.blockUnsupportedBrowsers
        if (OTHER_BROWSER in a.phone) {
            if (level <= ProtectionLevel.STANDARD) {
                unsupported = false
                notes += "You use another browser, so it's left open. NoXXX can't read its addresses, but images in it are still checked."
            } else {
                notes += "Heads up: Block unsupported browsers will close your other browser. Switch to a supported one, " +
                    "or turn it off on the next step."
            }
        }

        if (GAMING in a.phone) {
            notes += "If image detection flags a game by mistake, you can skip it later under Protection → Images."
        }

        val settings = LevelSettings(
            sensitivity = level.sensitivity,
            coolOffMinutes = level.coolOffDays * 24 * 60,
            blockUnsupportedBrowsers = unsupported,
            blockPrivateBrowsing = true,
            checkSearchBoxes = searchBoxes,
            requirePrivateDns = privateDns,
            tamperProtection = true,
            scanMessagingApps = level.scanMessagingApps,
            blockApps = blockApps,
        )
        return Bundle(level, settings, notes)
    }

    /**
     * Goals to pre-check, most personal first, capped at [MAX_GOALS] and topped up to [MIN_GOALS] so the list is
     * never empty or overwhelming.
     */
    fun suggestGoals(a: Answers): List<SuggestedGoal> {
        val picks = linkedMapOf<String, String>()
        fun pick(id: String, reason: String, ok: Boolean) {
            if (ok && id !in picks) picks[id] = reason
        }
        val hard = a.hardTimes
        val people = a.supporters
        pick("pray-morning", "You want to build a habit of prayer", PRAYER in a.faith)
        pick("scriptures", "You chose scripture study", SCRIPTURES in a.faith)
        pick("phone-away", "You said late nights are hard", LATE in hard)
        pick("phone-away", "You said being tired is hard", TIRED in hard)
        pick("check-in", "Someone knows and supports you", people.isNotEmpty())
        pick("bishop", "Your bishop or branch president knows", BISHOP in people)
        pick("arp", "You named a recovery group", GROUP in people)
        pick("reach-out", "You said loneliness is hard", LONELY in hard)
        pick("reach-out", "You said being alone is hard", ALONE in hard)
        pick("move", "You said stress is hard", STRESSED in hard)
        pick("move", "You said boredom is hard", BORED in hard)
        pick("pray-evening", "You want to build a habit of prayer", PRAYER in a.faith)
        pick("cfm", "You chose Come, Follow Me", COME_FOLLOW_ME in a.faith)
        pick("journal", "You chose journaling", JOURNAL in a.faith)
        pick("serve", "You chose serving others", SERVICE in a.faith)
        pick("shared-spaces", "You said being alone is hard", ALONE in hard)
        pick("note-trigger", "Past slips came quickly, and noticing triggers helps", a.attempts == Attempts.QUICK_SLIPS)
        pick("note-trigger", "Your last slip was recent, and noticing triggers helps", a.lastSlip?.isRecent == true)
        pick("sleep", "You said being tired is hard", TIRED in hard)
        pick("talk", "Steady study helps long streaks last", a.attempts == Attempts.LONG_THEN_RELAPSE)

        val chosen = picks.entries.take(MAX_GOALS).associate { it.key to it.value }.toMutableMap()
        // Someone who turned off every faith habit gets practical goals to start, not prayer.
        val fillers = if (a.faith.isEmpty()) listOf("reach-out", "move", "journal", "note-trigger")
            else RecoveryState.DEFAULT_GOALS.map { it.id }
        for (id in fillers) {
            if (chosen.size >= MIN_GOALS) break
            if (id !in chosen) chosen[id] = "A simple place to start"
        }
        return GoalLibrary.all.mapNotNull { e -> chosen[e.goal.id]?.let { SuggestedGoal(e.goal, it) } }
    }

    /** Built-in goals in library order, then the user's own goals in the order they added them. */
    fun ordered(goals: List<Goal>): List<Goal> {
        val byId = goals.associateBy { it.id }
        return GoalLibrary.all.mapNotNull { byId[it.goal.id] } + goals.filter { !GoalLibrary.isBuiltIn(it.id) }
    }

    /** Checks or unchecks a goal on the setup checklist and remembers that the user chose it. */
    fun checkGoal(s: RecoveryState, goal: Goal, on: Boolean): RecoveryState {
        val goals = if (on) s.goals.filter { it.id != goal.id } + goal else s.goals.filter { it.id != goal.id }
        return s.copy(goals = ordered(goals), setup = s.setup.copy(touchedGoals = s.setup.touchedGoals + goal.id))
    }

    /**
     * Fills in goals and the planned lockdown from [rec]. Goals the user checked or unchecked by hand keep their
     * state and their own goals are always kept; every other built-in goal follows the suggestion. A level or
     * lockdown picked by hand wins over the suggested one.
     */
    fun recompute(s: RecoveryState, rec: Recommendation): RecoveryState {
        val suggested = rec.goals.map { it.goal.id }.toSet()
        val current = s.goals.associateBy { it.id }
        val builtIn = GoalLibrary.all.map { it.goal }.filter {
            if (it.id in s.setup.touchedGoals) it.id in current else it.id in suggested
        }.map { current[it.id] ?: it }
        val level = s.setup.level ?: rec.level
        return s.copy(
            goals = builtIn + s.goals.filter { !GoalLibrary.isBuiltIn(it.id) },
            plannedLockdownDays = s.setup.lockdownDays ?: level.lockdownDays,
        )
    }

    /**
     * Writes level settings into the policy. Website, word, image and tamper checks are always turned on. Social
     * apps the [previous] level added are swapped for the new ones, and never-block apps are never added.
     */
    fun applyTo(p: Policy, s: LevelSettings, previous: LevelSettings? = null) = p.copy(
        urlFilter = true, builtInKeywords = true, imageScan = true,
        sensitivity = s.sensitivity, coolOffMinutes = s.coolOffMinutes,
        blockUnsupportedBrowsers = s.blockUnsupportedBrowsers, blockPrivateBrowsing = s.blockPrivateBrowsing,
        checkSearchBoxes = s.checkSearchBoxes, requirePrivateDns = s.requirePrivateDns,
        tamperProtection = s.tamperProtection, scanMessagingApps = s.scanMessagingApps,
        blockedApps = p.blockedApps - previous?.blockApps.orEmpty() + (s.blockApps - p.neverBlockApps),
    )
}

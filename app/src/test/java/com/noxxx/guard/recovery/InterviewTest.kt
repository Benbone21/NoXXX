package com.noxxx.guard.recovery

import com.noxxx.guard.policy.Policy
import com.noxxx.guard.policy.Sensitivity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewTest {
    private fun level(a: Answers) = Interview.recommend(a, emptySet()).level

    /** Light on every point-earning answer. */
    private val calm = Answers(frequency = Frequency.RARELY, lastSlip = LastSlip.LONGER)

    /** Heavy on every point-earning answer: 3 + 1 + 2 + 2 + 2 = 10 points. */
    private val heavy = Answers(
        frequency = Frequency.DAILY, duration = Duration.DECADE, attempts = Attempts.LONG_THEN_RELAPSE,
        lastSlip = LastSlip.DAYS, sources = setOf(Interview.SEARCH, Interview.SOCIAL, Interview.SITES),
    )

    @Test fun strictnessSetsTheStartingLevel() {
        assertEquals(ProtectionLevel.LIGHT, level(calm.copy(strictness = Strictness.GENTLE)))
        assertEquals(ProtectionLevel.STANDARD, level(calm.copy(strictness = Strictness.FIRM)))
        assertEquals(ProtectionLevel.STRICT, level(calm.copy(strictness = Strictness.STRICT)))
        assertEquals(ProtectionLevel.MAXIMUM, level(calm.copy(strictness = Strictness.MAXIMUM)))
        assertEquals("unanswered counts as firm", ProtectionLevel.STANDARD, level(calm))
    }

    @Test fun pointsAddUpFromEveryAnswer() {
        assertEquals(0, Interview.recommend(calm, emptySet()).points)
        assertEquals(10, Interview.recommend(heavy, emptySet()).points)
        val mid = Answers(frequency = Frequency.OFTEN, duration = Duration.YEARS, attempts = Attempts.QUICK_SLIPS,
            lastSlip = LastSlip.WEEK, sources = setOf(Interview.SEARCH))
        assertEquals(2 + 1 + 1 + 1 + 1, Interview.recommend(mid, emptySet()).points)
        assertEquals("private and unanswered slips add nothing", 1,
            Interview.recommend(Answers(lastSlip = LastSlip.PRIVATE), emptySet()).points)
    }

    @Test fun pointsMoveTheLevelUp() {
        // Firm: 4 points stays, 5 moves up one step, 8 two.
        val four = Answers(strictness = Strictness.FIRM, frequency = Frequency.OFTEN, sources = setOf(Interview.SEARCH, Interview.SOCIAL))
        assertEquals(ProtectionLevel.STANDARD, level(four))
        assertEquals(ProtectionLevel.STRICT, level(four.copy(lastSlip = LastSlip.WEEK)))
        assertEquals(ProtectionLevel.MAXIMUM, level(heavy.copy(strictness = Strictness.FIRM)))
        assertEquals("long streaks then relapse pushes up", ProtectionLevel.STRICT,
            level(four.copy(attempts = Attempts.LONG_THEN_RELAPSE)))
    }

    @Test fun gentleCapsAtStandardUnlessDailyWithARecentSlip() {
        val gentle = heavy.copy(strictness = Strictness.GENTLE, frequency = Frequency.OFTEN)
        val r = Interview.recommend(gentle, emptySet())
        assertEquals(ProtectionLevel.STANDARD, r.level)
        assertTrue(r.why.any { "gentle" in it && "Standard" in it })
        assertEquals(ProtectionLevel.STRICT, level(heavy.copy(strictness = Strictness.GENTLE)))
        assertEquals("an older slip keeps the cap", ProtectionLevel.STANDARD,
            level(heavy.copy(strictness = Strictness.GENTLE, lastSlip = LastSlip.MONTH, attempts = Attempts.LONG_THEN_RELAPSE)))
    }

    @Test fun asLockedDownAsPossibleAlwaysGivesMaximum() {
        val r = Interview.recommend(calm.copy(strictness = Strictness.MAXIMUM, support = setOf(Interview.BISHOP)), emptySet())
        assertEquals(ProtectionLevel.MAXIMUM, r.level)
        assertEquals(listOf("You asked for as locked down as possible."), r.why)
    }

    @Test fun accountabilitySoftensOneStepButNotBelowTheFloor() {
        val pushed = Answers(strictness = Strictness.FIRM, frequency = Frequency.WEEKLY, lastSlip = LastSlip.DAYS,
            sources = setOf(Interview.SEARCH, Interview.SOCIAL))
        assertEquals(ProtectionLevel.STRICT, level(pushed))
        val supported = Interview.recommend(pushed.copy(support = setOf(Interview.SPOUSE)), emptySet())
        assertEquals(ProtectionLevel.STANDARD, supported.level)
        assertTrue(supported.why.last().startsWith("Someone knows"))
        assertEquals("never below what strictness asks", ProtectionLevel.STRICT,
            level(calm.copy(strictness = Strictness.STRICT, support = setOf(Interview.GROUP))))
        assertEquals("never below Standard for frequent use", ProtectionLevel.STANDARD,
            level(Answers(strictness = Strictness.GENTLE, frequency = Frequency.DAILY, duration = Duration.YEARS, lastSlip = LastSlip.MONTH,
                sources = setOf(Interview.SEARCH), support = setOf(Interview.FAMILY))))
        assertEquals("no one yet isn't support", ProtectionLevel.STRICT, level(pushed.copy(support = setOf(Interview.NO_ONE))))
    }

    @Test fun whyNamesTheAnswersBehindTheLevel() {
        val r = Interview.recommend(heavy.copy(strictness = Strictness.FIRM), emptySet())
        assertTrue(r.why.first().contains("solid protection"))
        assertTrue(r.why.any { "every day" in it })
        assertTrue(r.why.any { "last day or two" in it })
        assertTrue(r.why.any { "long streaks" in it })
        assertTrue(r.why.any { "3 places" in it })
        assertTrue(r.why.any { "two steps" in it })
        assertTrue(Interview.recommend(calm.copy(strictness = Strictness.FIRM), emptySet()).why.last().contains("don't call for more"))
    }

    @Test fun levelsSetTheirBundle() {
        fun s(l: ProtectionLevel) = Interview.bundle(l, Answers(), emptySet()).settings
        assertEquals(Sensitivity.RELAXED, s(ProtectionLevel.LIGHT).sensitivity)
        assertEquals(listOf(1, 2, 3, 7).map { it * 24 * 60 }, ProtectionLevel.entries.map { s(it).coolOffMinutes })
        assertEquals(listOf(0, 7, 14, 30), ProtectionLevel.entries.map { it.lockdownDays })
        assertFalse(s(ProtectionLevel.LIGHT).blockUnsupportedBrowsers)
        assertTrue(s(ProtectionLevel.STRICT).blockUnsupportedBrowsers)
        assertTrue(s(ProtectionLevel.MAXIMUM).scanMessagingApps && s(ProtectionLevel.MAXIMUM).sensitivity == Sensitivity.STRICT)
        ProtectionLevel.entries.forEach {
            assertTrue(s(it).blockPrivateBrowsing && s(it).tamperProtection && s(it).requirePrivateDns)
        }
        assertEquals("Images: balanced · Loosening waits 3 days · 14-day lockdown", ProtectionLevel.STRICT.summary)
        assertEquals("Images: relaxed · Loosening waits 1 day · No lockdown", ProtectionLevel.LIGHT.summary)
    }

    @Test fun searchAsASourceKeepsSearchBoxesCheckedAtLight() {
        assertFalse(Interview.bundle(ProtectionLevel.LIGHT, Answers(), emptySet()).settings.checkSearchBoxes)
        val b = Interview.bundle(ProtectionLevel.LIGHT, Answers(sources = setOf(Interview.SEARCH)), emptySet())
        assertTrue(b.settings.checkSearchBoxes)
        assertEquals(1, b.notes.size)
    }

    @Test fun socialAppsAreBlockedOnlyWhenNamedAndInstalledExceptAtMaximum() {
        val installed = setOf("com.reddit.frontpage", "com.instagram.android", "com.example.notes")
        val named = Answers(sources = setOf(Interview.SOCIAL))
        assertEquals(installed - "com.example.notes", Interview.bundle(ProtectionLevel.STANDARD, named, installed).settings.blockApps)
        assertTrue(Interview.bundle(ProtectionLevel.STRICT, Answers(), installed).settings.blockApps.isEmpty())
        assertEquals(installed - "com.example.notes", Interview.bundle(ProtectionLevel.MAXIMUM, Answers(), installed).settings.blockApps)
        val light = Interview.bundle(ProtectionLevel.LIGHT, named, installed)
        assertTrue(light.settings.blockApps.isEmpty())
        assertTrue(light.notes.any { "Apps step" in it })
    }

    @Test fun vpnTurnsOffPrivateDnsAtEveryLevel() {
        val a = Answers(phone = setOf(Interview.VPN))
        ProtectionLevel.entries.forEach {
            val b = Interview.bundle(it, a, emptySet())
            assertFalse(b.settings.requirePrivateDns)
            assertTrue(b.notes.any { n -> "VPN" in n })
        }
    }

    @Test fun anotherBrowserIsLeftOpenAtLowerLevelsAndWarnedAboutAbove() {
        val a = Answers(phone = setOf(Interview.OTHER_BROWSER))
        assertFalse(Interview.bundle(ProtectionLevel.STANDARD, a, emptySet()).settings.blockUnsupportedBrowsers)
        assertFalse(Interview.bundle(ProtectionLevel.LIGHT, a, emptySet()).settings.blockUnsupportedBrowsers)
        val strict = Interview.bundle(ProtectionLevel.STRICT, a, emptySet())
        assertTrue(strict.settings.blockUnsupportedBrowsers)
        assertTrue(strict.notes.single().startsWith("Heads up"))
        assertTrue(Interview.bundle(ProtectionLevel.STANDARD, Answers(phone = setOf(Interview.GAMING)), emptySet())
            .notes.single().contains("Protection → Images"))
    }

    @Test fun applyingWritesTheBundleAndSwapsSocialBlocks() {
        val a = Answers(sources = setOf(Interview.SOCIAL))
        val strict = Interview.bundle(ProtectionLevel.STRICT, a, setOf("com.tumblr", "com.pinterest")).settings
        val p = Interview.applyTo(Policy(imageScan = false, blockedApps = setOf("a.b"), neverBlockApps = setOf("com.pinterest")), strict)
        assertTrue(p.imageScan && p.urlFilter && p.builtInKeywords && p.blockPrivateBrowsing && p.tamperProtection)
        assertEquals("never-block apps aren't added", setOf("a.b", "com.tumblr"), p.blockedApps)
        assertEquals(3 * 24 * 60, p.coolOffMinutes)
        val light = Interview.bundle(ProtectionLevel.LIGHT, a, setOf("com.tumblr")).settings
        val back = Interview.applyTo(p, light, previous = strict)
        assertEquals(setOf("a.b"), back.blockedApps)
        assertEquals(Sensitivity.RELAXED, back.sensitivity)
        assertFalse(back.blockUnsupportedBrowsers)
    }

    @Test fun goalsFollowTheAnswersWithReasons() {
        val r = Interview.recommend(Answers(faith = setOf(Interview.SCRIPTURES), hardTimes = setOf(Interview.LATE, Interview.LONELY),
            support = setOf(Interview.BISHOP)), emptySet())
        val byId = r.goals.associate { it.goal.id to it.reason }
        assertEquals(setOf("scriptures", "phone-away", "reach-out", "check-in", "bishop"), byId.keys)
        assertEquals("You said late nights are hard", byId["phone-away"])
        assertEquals("library order", GoalLibrary.all.map { it.goal.id }.filter { it in byId }, r.goals.map { it.goal.id })
        val group = Interview.suggestGoals(Answers(support = setOf(Interview.GROUP))).map { it.goal.id }
        assertTrue("arp" in group && "check-in" in group)
        assertFalse(Interview.suggestGoals(Answers(support = setOf(Interview.NO_ONE))).any { it.goal.id == "check-in" })
    }

    @Test fun goalSuggestionsStayBetweenFourAndSeven() {
        assertEquals(RecoveryState.DEFAULT_GOALS.map { it.id }.toSet(),
            Interview.suggestGoals(Answers()).map { it.goal.id }.toSet())
        val noFaith = Interview.suggestGoals(Answers(faith = emptySet())).map { it.goal.id }
        assertEquals(4, noFaith.size)
        assertFalse(noFaith.any { it.startsWith("pray") })
        val everything = Answers(
            faith = Interview.FAITH.toSet(), hardTimes = Interview.HARD_TIMES.toSet(), support = setOf(Interview.BISHOP, Interview.GROUP),
            attempts = Attempts.QUICK_SLIPS,
        )
        val many = Interview.suggestGoals(everything)
        assertEquals(Interview.MAX_GOALS, many.size)
        assertTrue("most personal kept", many.map { it.goal.id }.containsAll(listOf("pray-morning", "phone-away", "check-in", "bishop")))
    }

    @Test fun goalIdsAreStableAndUnique() {
        val ids = GoalLibrary.all.map { it.goal.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(listOf("pray-morning", "pray-evening", "scriptures", "cfm", "journal", "serve", "phone-away", "reach-out", "move")))
        assertTrue(ids.containsAll(RecoveryState.DEFAULT_GOALS.map { it.id }))
    }

    @Test fun recomputeKeepsHandChoicesAndOwnGoals() {
        val own = Goal("goal-1", "Call my brother")
        var s = RecoveryState(goals = RecoveryState.DEFAULT_GOALS + own)
        s = Interview.recompute(s, Interview.recommend(Answers(hardTimes = setOf(Interview.LATE)), emptySet()))
        assertTrue(s.goals.any { it.id == "phone-away" })
        assertEquals(own, s.goals.last())
        assertEquals(7, s.plannedLockdownDays)

        // By hand: uncheck phone-away, check temple, pick Light and a 30-day lockdown.
        s = Interview.checkGoal(s, GoalLibrary.goal("phone-away"), false)
        s = Interview.checkGoal(s, GoalLibrary.goal("temple"), true)
        s = s.copy(setup = s.setup.copy(level = ProtectionLevel.LIGHT, lockdownDays = 30))

        // New answers: late nights still hard, now stressed too.
        s = Interview.recompute(s, Interview.recommend(Answers(hardTimes = setOf(Interview.LATE, Interview.STRESSED)), emptySet()))
        val ids = s.goals.map { it.id }
        assertFalse("unchecked by hand stays unchecked", "phone-away" in ids)
        assertTrue("checked by hand stays checked", "temple" in ids)
        assertTrue("untouched follows the new answers", "move" in ids)
        assertEquals(own, s.goals.last())
        assertEquals(30, s.plannedLockdownDays)

        s = Interview.recompute(s.copy(setup = s.setup.copy(lockdownDays = null)), Interview.recommend(Answers(), emptySet()))
        assertEquals("follows the hand-picked level", 0, s.plannedLockdownDays)
    }

    @Test fun recomputeIsStableForTheSameAnswers() {
        val rec = Interview.recommend(Answers(hardTimes = setOf(Interview.BORED)), emptySet())
        val once = Interview.recompute(RecoveryState(), rec)
        assertEquals(once, Interview.recompute(once, rec))
    }

    @Test fun noOneYetCantBePickedWithAPerson() {
        assertEquals(setOf(Interview.NO_ONE), Interview.toggleSupport(setOf(Interview.SPOUSE, Interview.FAMILY), Interview.NO_ONE))
        assertEquals(setOf(Interview.BISHOP), Interview.toggleSupport(setOf(Interview.NO_ONE), Interview.BISHOP))
        assertEquals(setOf(Interview.SPOUSE), Interview.toggleSupport(setOf(Interview.SPOUSE, Interview.BISHOP), Interview.BISHOP))
    }

    @Test fun oldSavedAnswersStillLoad() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """{"goals":[{"id":"pray-morning","title":"Morning prayer"}],"answers":{"duration":"YEARS","frequency":"DAILY",""" +
            """"attempts":"FIRST","hardTimes":["Bored"],"sources":[],"hasSupport":true,"faith":["Prayer"]},"plannedLockdownDays":30}"""
        val s = json.decodeFromString(RecoveryState.serializer(), old)
        val a = s.answers!!
        assertEquals(Frequency.DAILY, a.frequency)
        assertEquals(setOf(Interview.FAMILY), a.supporters)
        assertNull(a.strictness)
        assertNull(a.lastSlip)
        assertEquals(SetupChoices(), s.setup)
        assertTrue(json.decodeFromString(Answers.serializer(), """{"hasSupport":false}""").supporters.isEmpty())
        // A new answer replaces the old one, and survives a round trip.
        val updated = a.copy(support = setOf(Interview.NO_ONE), legacyHasSupport = false)
        assertTrue(updated.supporters.isEmpty())
        assertEquals(updated, json.decodeFromString(Answers.serializer(), json.encodeToString(Answers.serializer(), updated)))
        val withSetup = s.copy(setup = SetupChoices(level = ProtectionLevel.STRICT,
            applied = Interview.bundle(ProtectionLevel.STRICT, a, emptySet()).settings))
        assertEquals(withSetup, json.decodeFromString(RecoveryState.serializer(), json.encodeToString(RecoveryState.serializer(), withSetup)))
    }
}

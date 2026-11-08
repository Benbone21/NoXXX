package com.noxxx.guard.recovery

import com.noxxx.guard.policy.Policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewTest {
    @Test fun moreFrequentUseGetsStrongerSettings() {
        val light = Interview.recommend(Answers(frequency = Frequency.RARELY), emptySet())
        val heavy = Interview.recommend(Answers(frequency = Frequency.DAILY), emptySet())
        assertEquals(0, light.lockdownDays)
        assertEquals(30, heavy.lockdownDays)
        assertTrue(heavy.coolOffMinutes > light.coolOffMinutes)
    }

    @Test fun relapseAfterLongStreaksStillGetsALockdown() {
        val r = Interview.recommend(Answers(frequency = Frequency.RARELY, attempts = Attempts.LONG_THEN_RELAPSE), emptySet())
        assertEquals(14, r.lockdownDays)
    }

    @Test fun goalsFollowFaithChoicesAndTriggers() {
        val r = Interview.recommend(Answers(faith = setOf(Interview.SCRIPTURES), hardTimes = setOf(Interview.LATE, Interview.LONELY)), emptySet())
        assertEquals(listOf("scriptures", "phone-away", "reach-out"), r.goals.map { it.id })
        assertEquals(RecoveryState.DEFAULT_GOALS, Interview.recommend(Answers(faith = emptySet()), emptySet()).goals)
    }

    @Test fun socialAppsArePreCheckedOnlyWhenInstalledAndNamed() {
        val installed = setOf("com.reddit.frontpage", "com.example.notes")
        assertEquals(setOf("com.reddit.frontpage"), Interview.recommend(Answers(sources = setOf(Interview.SOCIAL)), installed).blockApps)
        assertTrue(Interview.recommend(Answers(sources = setOf(Interview.SEARCH)), installed).blockApps.isEmpty())
    }

    @Test fun applyingTurnsEverythingOnAndKeepsExistingBlocks() {
        val r = Interview.recommend(Answers(frequency = Frequency.OFTEN, sources = setOf(Interview.SOCIAL)), setOf("com.tumblr"))
        val p = Interview.applyTo(Policy(imageScan = false, blockedApps = setOf("a.b")), r)
        assertTrue(p.imageScan && p.blockPrivateBrowsing && p.tamperProtection)
        assertEquals(setOf("a.b", "com.tumblr"), p.blockedApps)
        assertEquals(72 * 60, p.coolOffMinutes)
    }
}

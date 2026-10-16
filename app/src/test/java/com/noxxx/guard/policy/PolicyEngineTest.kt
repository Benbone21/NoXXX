package com.noxxx.guard.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    private val hour = 60 * 60_000L
    private val locked = GuardState(committed = true, policy = Policy(coolOffMinutes = 60))

    private fun unlocked(): GuardState {
        val requested = PolicyEngine.requestUnlock(locked)!!
        return PolicyEngine.openUnlock(PolicyEngine.advance(requested, hour))!!
    }

    @Test fun setupModeAppliesEverythingImmediately() {
        val (s, r) = PolicyEngine.request(GuardState()) { it.copy(imageScan = false) }
        assertEquals(ChangeResult.Applied, r)
        assertEquals(false, s.policy.imageScan)
    }

    @Test fun stricterChangesApplyWhileLocked() {
        val (s, r) = PolicyEngine.request(locked) { it.copy(blockedApps = it.blockedApps + "a.b") }
        assertEquals(ChangeResult.Applied, r)
        assertTrue("a.b" in s.policy.blockedApps)
    }

    @Test fun looseningNeedsAnUnlock() {
        val (s, r) = PolicyEngine.request(locked) { it.copy(imageScan = false) }
        assertEquals(ChangeResult.NeedsUnlock, r)
        assertEquals(true, s.policy.imageScan)
    }

    @Test fun scanningMessagingAppsIsStricterAndAppliesWhileLocked() {
        val (s, r) = PolicyEngine.request(locked) { it.copy(scanMessagingApps = true) }
        assertEquals(ChangeResult.Applied, r)
        assertTrue(s.policy.scanMessagingApps)
    }

    @Test fun unScanningMessagingAppsNeedsAnUnlock() {
        val on = locked.copy(policy = locked.policy.copy(scanMessagingApps = true))
        val (s, r) = PolicyEngine.request(on) { it.copy(scanMessagingApps = false) }
        assertEquals(ChangeResult.NeedsUnlock, r)
        assertTrue(s.policy.scanMessagingApps)
    }

    @Test fun addingToNeverBlockIsLooseningAndNeedsUnlock() {
        val (s, r) = PolicyEngine.request(locked) { it.copy(neverBlockApps = it.neverBlockApps + "com.bank.app") }
        assertEquals(ChangeResult.NeedsUnlock, r)
        assertTrue(s.policy.neverBlockApps.isEmpty())
    }

    @Test fun removingFromNeverBlockIsStricterAndApplies() {
        val on = locked.copy(policy = locked.policy.copy(neverBlockApps = setOf("com.bank.app")))
        val (s, r) = PolicyEngine.request(on) { it.copy(neverBlockApps = emptySet()) }
        assertEquals(ChangeResult.Applied, r)
        assertTrue(s.policy.neverBlockApps.isEmpty())
    }

    @Test fun unlockWaitsOutTheCoolOffThenOpensAWindow() {
        val requested = PolicyEngine.requestUnlock(locked)!!
        assertTrue(requested.unlock!!.isWaiting)
        assertFalse(requested.settingsEditable)
        assertNull(PolicyEngine.openUnlock(PolicyEngine.advance(requested, hour - 1)))

        val ready = PolicyEngine.advance(requested, hour)
        assertTrue(ready.unlock!!.isReady)
        assertFalse("ready isn't open yet", ready.settingsEditable)

        val open = PolicyEngine.openUnlock(ready)!!
        assertTrue(open.settingsEditable)
        val (changed, r) = PolicyEngine.request(open) { it.copy(imageScan = false) }
        assertEquals(ChangeResult.Applied, r)
        assertEquals(false, changed.policy.imageScan)
    }

    @Test fun windowClosesAfterFifteenMinutes() {
        val open = unlocked()
        val almost = PolicyEngine.advance(open, UnlockRequest.WINDOW_MS - 1)
        assertTrue(almost.settingsEditable)
        val closed = PolicyEngine.advance(open, UnlockRequest.WINDOW_MS)
        assertNull(closed.unlock)
        assertFalse(closed.settingsEditable)
    }

    @Test fun readyUnlockLapsesIfNeverOpened() {
        val requested = PolicyEngine.requestUnlock(locked)!!
        val lapsed = PolicyEngine.advance(requested, hour + UnlockRequest.READY_MS)
        assertNull(lapsed.unlock)
    }

    @Test fun relockClosesTheWindowEarly() {
        assertFalse(PolicyEngine.relock(unlocked()).settingsEditable)
    }

    @Test fun lockdownRefusesUnlockAndLooseningButAllowsStricter() {
        val ld = PolicyEngine.startLockdown(locked, 24 * hour)
        assertNull(PolicyEngine.requestUnlock(ld))
        assertTrue(PolicyEngine.request(ld) { it.copy(urlFilter = false) }.second is ChangeResult.Locked)
        assertEquals(ChangeResult.Applied, PolicyEngine.request(ld) { it.copy(sensitivity = Sensitivity.STRICT) }.second)
    }

    @Test fun startingLockdownCancelsAnUnlock() {
        val ld = PolicyEngine.startLockdown(unlocked(), hour)
        assertNull(ld.unlock)
        assertFalse(ld.settingsEditable)
    }

    @Test fun lockdownCannotBeShortened() {
        val ld = PolicyEngine.startLockdown(locked, 24 * hour)
        assertEquals(24 * hour, PolicyEngine.startLockdown(ld, hour).lockdownRemainingMs)
    }

    @Test fun unlockWaitUsesTheCoolOffAtRequestTime() {
        val long = locked.copy(policy = locked.policy.copy(coolOffMinutes = 48 * 60))
        assertEquals(48 * hour, PolicyEngine.requestUnlock(long)!!.unlock!!.requiredMs)
    }

    @Test fun partnerUnlockOpensImmediatelyAndEndsLockdown() {
        val ld = PolicyEngine.startLockdown(locked, 24 * hour)
        val p = PolicyEngine.partnerUnlock(ld)
        assertTrue(p.settingsEditable)
        assertEquals(0, p.lockdownRemainingMs)
    }

    @Test fun openFullUnlockAlsoAllowsEditingSettings() {
        val requested = PolicyEngine.requestFullUnlock(locked)!!
        val open = PolicyEngine.openFullUnlock(PolicyEngine.advance(requested, PolicyEngine.FULL_UNLOCK_COOL_OFF_MS))!!
        assertTrue(open.tamperShieldOff)
        assertTrue("a full unlock covers everything a settings unlock does", open.settingsEditable)
    }

    @Test fun partnerPasswordCanBeAddedDuringSetup() {
        assertTrue(PolicyEngine.canAddPartner(GuardState()))
    }

    @Test fun partnerPasswordCannotBeAddedOnceProtectionStarts() {
        assertFalse(PolicyEngine.canAddPartner(locked))
    }

    @Test fun partnerPasswordCannotBeAddedEvenInsideAnUnlockWindow() {
        assertFalse("it would make every later unlock instant", PolicyEngine.canAddPartner(unlocked()))
    }

    @Test fun resetStreakKeepsTheBest() {
        val s = locked.copy(streakStartWallMs = 0)
        val reset = PolicyEngine.resetStreak(s, nowWallMs = 10 * 86_400_000L)
        assertEquals(10 * 86_400_000L, reset.bestStreakMs)
        val again = PolicyEngine.resetStreak(reset, nowWallMs = 12 * 86_400_000L)
        assertEquals("a shorter streak doesn't replace the best", 10 * 86_400_000L, again.bestStreakMs)
    }

    @Test fun switchingBetweenApprovedDnsProvidersIsNotWeaker() {
        assertTrue(Policy().copy(privateDnsHost = "family-filter-dns.cleanbrowsing.org").isAtLeastAsStrictAs(Policy()))
        assertNotNull(Policy().copy(privateDnsHost = "dns.example").weakeningsFrom(Policy()).firstOrNull())
    }
}

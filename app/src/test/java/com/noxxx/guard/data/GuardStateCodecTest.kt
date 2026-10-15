package com.noxxx.guard.data

import com.noxxx.guard.policy.GuardState
import com.noxxx.guard.policy.Policy
import com.noxxx.guard.policy.Sensitivity
import com.noxxx.guard.policy.TamperEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardStateCodecTest {
    private val now = 1_700_000_000_000L

    private fun assertLockedFallback(loaded: LoadedState) {
        assertNotNull(loaded.error)
        val s = loaded.state
        assertTrue(s.committed)
        assertTrue(s.onboardingDone)
        assertFalse(s.settingsEditable)
        assertEquals(Policy(), s.policy)
        assertEquals(now, s.streakStartWallMs)
        assertEquals(1, s.tamperEvents.size)
        assertEquals(now, s.tamperEvents.single().timeMs)
    }

    @Test fun noSavedStateIsAFirstRunInSetupMode() {
        val loaded = GuardStateCodec.decode(null, now)
        assertNull(loaded.error)
        assertFalse(loaded.state.committed)
        assertTrue(loaded.state.settingsEditable)
        assertEquals(now, loaded.state.streakStartWallMs)
    }

    @Test fun savedStateRoundTrips() {
        val saved = GuardState(
            committed = true,
            onboardingDone = true,
            policy = Policy(imageScan = false, sensitivity = Sensitivity.STRICT, blockedApps = setOf("a.b")),
            lockdownRemainingMs = 5_000,
            streakStartWallMs = 42,
            tamperEvents = listOf(TamperEvent(7, "x")),
            tamperCount = 3,
        )
        val loaded = GuardStateCodec.decode(GuardStateCodec.encode(saved), now)
        assertNull(loaded.error)
        assertEquals(saved, loaded.state)
    }

    @Test fun setupModeStateStaysInSetupMode() {
        val loaded = GuardStateCodec.decode(GuardStateCodec.encode(GuardState(streakStartWallMs = 1)), now)
        assertNull(loaded.error)
        assertFalse(loaded.state.committed)
    }

    @Test fun corruptStateFailsClosed() {
        assertLockedFallback(GuardStateCodec.decode("""{"committed":true,"policy":{"urlFil""", now))
    }

    @Test fun emptyStateFailsClosed() {
        assertLockedFallback(GuardStateCodec.decode("", now))
    }

    @Test fun nonObjectStateFailsClosed() {
        assertLockedFallback(GuardStateCodec.decode("[1,2,3]", now))
    }

    @Test fun missingCommittedFlagFailsClosed() {
        assertLockedFallback(GuardStateCodec.decode("""{"onboardingDone":true}""", now))
    }

    @Test fun nullCommittedFlagFailsClosed() {
        assertLockedFallback(GuardStateCodec.decode("""{"committed":null}""", now))
    }

    @Test fun wrongTypeFailsClosed() {
        assertLockedFallback(GuardStateCodec.decode("""{"committed":true,"tamperCount":"lots"}""", now))
    }

    @Test fun unknownKeysAndEnumValuesStillDecode() {
        val raw = """{"committed":true,"newField":1,"policy":{"imageScan":false,"sensitivity":"EXTREME"}}"""
        val loaded = GuardStateCodec.decode(raw, now)
        assertNull(loaded.error)
        assertTrue(loaded.state.committed)
        assertFalse(loaded.state.policy.imageScan)
        assertEquals(Policy().sensitivity, loaded.state.policy.sensitivity)
    }

    @Test fun nullForNonNullableFieldUsesDefault() {
        val loaded = GuardStateCodec.decode("""{"committed":true,"blockedApps":null,"policy":{"blockedApps":null}}""", now)
        assertNull(loaded.error)
        assertTrue(loaded.state.committed)
        assertEquals(emptySet<String>(), loaded.state.policy.blockedApps)
    }
}

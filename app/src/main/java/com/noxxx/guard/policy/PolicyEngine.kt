package com.noxxx.guard.policy

sealed interface ChangeResult {
    data object NoChange : ChangeResult
    data object Applied : ChangeResult
    /** Settings are locked; loosening needs an unlock first. */
    data object NeedsUnlock : ChangeResult
    data class Locked(val remainingMs: Long) : ChangeResult
}

/**
 * Pure state transitions for the settings lock, unlock requests and lockdown.
 *
 * Once protection is committed, settings are locked: anything stricter still applies at once, but
 * loosening needs an unlock. An unlock waits out the cool-off, then opens a short edit window.
 */
object PolicyEngine {

    /** Full unlock requires 1 week of waiting. */
    const val FULL_UNLOCK_COOL_OFF_MS = 7L * 24 * 60 * 60_000

    fun request(state: GuardState, transform: (Policy) -> Policy): Pair<GuardState, ChangeResult> {
        val current = state.policy
        val proposed = transform(current)
        if (proposed == current) return state to ChangeResult.NoChange
        if (state.settingsEditable || proposed.isAtLeastAsStrictAs(current)) {
            return state.copy(policy = proposed) to ChangeResult.Applied
        }
        if (state.lockdownRemainingMs > 0) return state to ChangeResult.Locked(state.lockdownRemainingMs)
        return state to ChangeResult.NeedsUnlock
    }

    /** Starts waiting out the cool-off. Refused during lockdown; a no-op if a request already exists. */
    fun requestUnlock(state: GuardState): GuardState? {
        if (!state.committed || state.lockdownRemainingMs > 0) return null
        if (state.unlock != null) return state
        return state.copy(unlock = UnlockRequest(requiredMs = state.policy.coolOffMinutes * 60_000L))
    }

    /** Opens the edit window once the cool-off is over. */
    fun openUnlock(state: GuardState): GuardState? {
        val u = state.unlock ?: return null
        if (!u.isReady || state.lockdownRemainingMs > 0) return null
        return state.copy(unlock = u.copy(openedAtMs = u.elapsedMs))
    }

    /** Cancels a waiting request or closes an open window early. */
    fun relock(state: GuardState) = state.copy(unlock = null)

    /** Full unlock: 1-week cool-off before deactivating device admin / accessibility is allowed. */
    fun requestFullUnlock(state: GuardState): GuardState? {
        if (!state.committed || state.lockdownRemainingMs > 0) return null
        if (state.fullUnlock != null) return state
        return state.copy(fullUnlock = UnlockRequest(requiredMs = FULL_UNLOCK_COOL_OFF_MS))
    }

    fun openFullUnlock(state: GuardState): GuardState? {
        val u = state.fullUnlock ?: return null
        if (!u.isReady || state.lockdownRemainingMs > 0) return null
        return state.copy(fullUnlock = u.copy(openedAtMs = u.elapsedMs))
    }

    fun relockFull(state: GuardState) = state.copy(fullUnlock = null)

    /**
     * A partner password skips every future cool-off, so a new one can only be added during setup.
     * Not even an unlock window allows it, or one wait would buy instant unlocks forever.
     */
    fun canAddPartner(state: GuardState) = !state.committed

    /** Accountability partner: opens a full unlock immediately and ends any lockdown. */
    fun partnerUnlock(state: GuardState) =
        state.copy(fullUnlock = UnlockRequest(requiredMs = 0, openedAtMs = 0), lockdownRemainingMs = 0)

    /** Credits [deltaMs] of verified real time to the unlock requests and lockdown. */
    fun advance(state: GuardState, deltaMs: Long): GuardState {
        if (deltaMs <= 0) return state
        val unlock = state.unlock
            ?.let { it.copy(elapsedMs = it.elapsedMs + deltaMs) }
            ?.takeUnless { it.isExpired }
        val fullUnlock = state.fullUnlock
            ?.let { it.copy(elapsedMs = it.elapsedMs + deltaMs) }
            ?.takeUnless { it.isExpired }
        return state.copy(
            unlock = unlock,
            fullUnlock = fullUnlock,
            lockdownRemainingMs = (state.lockdownRemainingMs - deltaMs).coerceAtLeast(0),
        )
    }

    /** Ends the setup phase. From here on, settings are locked. */
    fun commit(state: GuardState) = state.copy(committed = true, unlock = null, fullUnlock = null)

    /** Lockdown can be started or extended at any time, never shortened. It cancels any unlock. */
    fun startLockdown(state: GuardState, durationMs: Long) =
        state.copy(lockdownRemainingMs = maxOf(state.lockdownRemainingMs, durationMs), unlock = null, fullUnlock = null)

    fun partnerEndLockdown(state: GuardState) = state.copy(lockdownRemainingMs = 0)

    /** Records the finished streak as the best if it was longer, then starts a new one. */
    fun resetStreak(state: GuardState, nowWallMs: Long) = state.copy(
        bestStreakMs = maxOf(state.bestStreakMs, nowWallMs - state.streakStartWallMs),
        streakStartWallMs = nowWallMs,
    )
}
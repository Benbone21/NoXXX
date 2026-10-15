package com.noxxx.guard.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.noxxx.guard.policy.ChangeResult
import com.noxxx.guard.policy.Clock
import com.noxxx.guard.policy.ClockReading
import com.noxxx.guard.policy.GuardState
import com.noxxx.guard.policy.Policy
import com.noxxx.guard.policy.PolicyEngine
import com.noxxx.guard.policy.Secret
import com.noxxx.guard.policy.TamperEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists [GuardState] and is the only place that mutates it. */
class GuardStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("guard_state", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<GuardState> = _state.asStateFlow()
    val policy: Policy get() = _state.value.policy

    private fun load(): GuardState {
        val raw = prefs.getString(KEY, null)
        val now = System.currentTimeMillis()
        val loaded = GuardStateCodec.decode(raw, now)
        if (loaded.error != null) {
            Log.e(TAG, "Saved state unreadable; locked with default policy. Original kept as $KEY_UNREADABLE$now", loaded.error)
            // Save the locked state now so it survives a restart, but keep the original instead of losing it.
            prefs.edit()
                .putString("$KEY_UNREADABLE$now", raw)
                .putString(KEY, GuardStateCodec.encode(loaded.state))
                .apply()
        }
        return loaded.state
    }

    private fun update(block: (GuardState) -> GuardState): GuardState = synchronized(lock) {
        val next = block(_state.value)
        if (next != _state.value) {
            _state.value = next
            prefs.edit().putString(KEY, GuardStateCodec.encode(next)).apply()
        }
        next
    }

    fun request(transform: (Policy) -> Policy): ChangeResult = synchronized(lock) {
        tick()
        val (next, result) = PolicyEngine.request(_state.value, transform)
        update { next }
        result
    }

    /** Credits monotonic time to the timers. Safe to call often. */
    fun tick() {
        update { s ->
            val (clock, delta) = Clock.tick(s.clock, reading())
            PolicyEngine.advance(s.copy(clock = clock), delta)
        }
    }

    fun onNetworkTime(networkMs: Long, elapsedAtFetchMs: Long) {
        update { s ->
            val (clock, credit) = Clock.onNetworkTime(s.clock, networkMs, elapsedAtFetchMs)
            PolicyEngine.advance(s.copy(clock = clock), credit)
        }
    }

    /** Returns false if an unlock can't be requested right now (lockdown or setup mode). */
    fun requestUnlock(): Boolean {
        tick()
        var ok = false
        update { s -> PolicyEngine.requestUnlock(s)?.also { ok = true } ?: s }
        return ok
    }

    fun openUnlock(): Boolean {
        tick()
        var ok = false
        update { s -> PolicyEngine.openUnlock(s)?.also { ok = true } ?: s }
        return ok
    }

    fun relock() = update { PolicyEngine.relock(it) }

    /** Full unlock: allows deactivating device admin / accessibility. 1-week cool-off. */
    fun requestFullUnlock(): Boolean {
        tick()
        var ok = false
        update { s -> PolicyEngine.requestFullUnlock(s)?.also { ok = true } ?: s }
        return ok
    }

    fun openFullUnlock(): Boolean {
        tick()
        var ok = false
        update { s -> PolicyEngine.openFullUnlock(s)?.also { ok = true } ?: s }
        return ok
    }

    fun relockFull() = update { PolicyEngine.relockFull(it) }

    fun commit() = update { PolicyEngine.commit(it) }

    fun finishOnboarding() = update { it.copy(onboardingDone = true) }

    fun startLockdown(durationMs: Long) = update { PolicyEngine.startLockdown(it, durationMs) }

    fun partnerUnlock(password: String): Boolean {
        if (!Secret.verify(password, _state.value.partnerHash)) return false
        update { PolicyEngine.partnerUnlock(it) }
        return true
    }

    fun partnerEndLockdown(password: String): Boolean {
        if (!Secret.verify(password, _state.value.partnerHash)) return false
        update { PolicyEngine.partnerEndLockdown(it) }
        return true
    }

    /** Adding a partner password works only during setup; changing or removing one needs the current one. */
    fun setPartnerPassword(current: String?, new: String?): Boolean {
        val existing = _state.value.partnerHash
        if (existing == null && !PolicyEngine.canAddPartner(_state.value)) return false
        if (existing != null && (current == null || !Secret.verify(current, existing))) return false
        update { it.copy(partnerHash = new?.takeIf { p -> p.isNotEmpty() }?.let(Secret::hash)) }
        return true
    }

    fun setPin(current: String?, new: String?): Boolean {
        val existing = _state.value.pinHash
        if (existing != null && (current == null || !Secret.verify(current, existing))) return false
        update { it.copy(pinHash = new?.takeIf { p -> p.isNotEmpty() }?.let(Secret::hash)) }
        return true
    }

    fun checkPin(pin: String) = Secret.verify(pin, _state.value.pinHash)

    fun resetStreak() = update { PolicyEngine.resetStreak(it, System.currentTimeMillis()) }

    fun recordTamper(reason: String) {
        val now = System.currentTimeMillis()
        update { s ->
            val event = TamperEvent(now, reason)
            val events = (listOf(event) + s.tamperEvents).take(MAX_TAMPER_EVENTS)
            PolicyEngine.resetStreak(
                s.copy(tamperEvents = events, tamperCount = s.tamperCount + 1),
                now,
            )
        }
    }

    fun clearTamperEvents() = update { it.copy(tamperEvents = emptyList()) }

    private fun reading() = ClockReading(
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0),
        elapsedMs = SystemClock.elapsedRealtime(),
    )

    private companion object {
        const val TAG = "GuardStore"
        const val KEY = "state"
        /** Prefix for copies of saved state that couldn't be decoded, suffixed with the wall time. */
        const val KEY_UNREADABLE = "state_unreadable_"
        const val MAX_TAMPER_EVENTS = 50
    }
}

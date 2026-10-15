package com.noxxx.guard.data

import com.noxxx.guard.policy.GuardState
import com.noxxx.guard.policy.TamperEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of [GuardStateCodec.decode]. [error] is set when saved state existed but couldn't be read. */
data class LoadedState(val state: GuardState, val error: Throwable? = null)

/**
 * JSON form of [GuardState] as stored by [GuardStore]. Pure, so it can be unit tested.
 *
 * Decoding fails closed: only a genuine first run (nothing saved) starts in setup mode. Saved state
 * that can't be read comes back committed with the default policy, never unlocked.
 */
object GuardStateCodec {
    /** Lenient about schema drift: unknown keys are skipped, unknown enum values and nulls take defaults. */
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    const val UNREADABLE_REASON = "Saved settings couldn't be read, so protection restarted locked with default settings"

    fun encode(state: GuardState): String = json.encodeToString(GuardState.serializer(), state)

    fun decode(raw: String?, nowWallMs: Long): LoadedState {
        if (raw == null) return LoadedState(GuardState(streakStartWallMs = nowWallMs))
        return runCatching {
            val element = json.parseToJsonElement(raw)
            // A missing or null flag would decode as false (setup mode), so it must be saved explicitly.
            requireNotNull(element.jsonObject["committed"]?.jsonPrimitive?.booleanOrNull) { "No committed flag" }
            json.decodeFromJsonElement(GuardState.serializer(), element)
        }.fold(
            onSuccess = { LoadedState(it) },
            onFailure = { LoadedState(lockedFallback(nowWallMs), it) },
        )
    }

    private fun lockedFallback(nowWallMs: Long) = GuardState(
        committed = true,
        onboardingDone = true,
        streakStartWallMs = nowWallMs,
        tamperEvents = listOf(TamperEvent(nowWallMs, UNREADABLE_REASON)),
        tamperCount = 1,
    )
}

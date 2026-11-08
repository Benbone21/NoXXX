package com.noxxx.guard.recovery

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class Goal(val id: String, val title: String)

/** A journal entry. [mood] 1 (rough) to 5 (great); [urge] 0 (none) to 5 (overwhelming). */
@Serializable
data class JournalEntry(
    val timeMs: Long,
    val mood: Int,
    val urge: Int,
    val triggers: Set<String> = emptySet(),
    val note: String = "",
    val slipped: Boolean = false,
)

/** Goals, which days each was done (epoch days), and the journal. Not covered by the settings lock. */
@Serializable
data class RecoveryState(
    val goals: List<Goal> = DEFAULT_GOALS,
    val done: Map<String, Set<Long>> = emptyMap(),
    val journal: List<JournalEntry> = emptyList(),
) {
    fun isDone(goalId: String, day: LocalDate) = day.toEpochDay() in done[goalId].orEmpty()

    fun toggle(goalId: String, day: LocalDate): RecoveryState {
        val d = day.toEpochDay()
        val days = done[goalId].orEmpty()
        return copy(done = done + (goalId to if (d in days) days - d else days + d))
    }

    /** Days in a row the goal was done, ending today, or yesterday if today isn't done yet. */
    fun streak(goalId: String, today: LocalDate): Int {
        val days = done[goalId].orEmpty()
        var d = today.toEpochDay().let { if (it in days) it else it - 1 }
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    fun addGoal(title: String, id: String): RecoveryState =
        if (title.isBlank()) this else copy(goals = goals + Goal(id, title.trim()))

    fun removeGoal(id: String) = copy(goals = goals.filter { it.id != id }, done = done - id)

    fun addEntry(entry: JournalEntry) = copy(journal = (listOf(entry) + journal).take(MAX_ENTRIES))

    fun removeEntry(timeMs: Long) = copy(journal = journal.filter { it.timeMs != timeMs })

    companion object {
        const val MAX_ENTRIES = 1000
        val DEFAULT_GOALS = listOf(
            Goal("pray-morning", "Morning prayer"),
            Goal("pray-evening", "Evening prayer"),
            Goal("scriptures", "Scripture study"),
            Goal("reach-out", "Reach out to someone"),
        )
        val TRIGGERS = listOf("Stressed", "Bored", "Lonely", "Tired", "Late at night", "Alone", "Angry", "Sad")
    }
}

class RecoveryStore(context: Context) {
    private val prefs = context.getSharedPreferences("recovery", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    private fun load(): RecoveryState = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(RecoveryState.serializer(), it) }.getOrNull() }
        ?: RecoveryState()

    @Synchronized
    fun update(block: (RecoveryState) -> RecoveryState) {
        val next = block(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit().putString(KEY, json.encodeToString(RecoveryState.serializer(), next)).apply()
    }

    private companion object {
        const val KEY = "state"
    }
}

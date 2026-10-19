package com.noxxx.guard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class BlockKind { WEBSITE, SEARCH, APP, IMAGE, PRIVATE_BROWSING, BROWSER, DNS_OFF, SETTINGS, GUARD_DISABLED }

@Serializable
data class BlockEvent(
    val timeMs: Long,
    val kind: BlockKind,
    val detail: String,
    /** Why it was blocked, safe to show (never the blocked word itself). Empty for events logged before 1.0.2. */
    val reason: String = "",
)

/** Block counts that survive clearing the history. [daily] is keyed by local epoch day. */
@Serializable
data class BlockStats(val total: Long = 0, val daily: Map<Long, Int> = emptyMap()) {
    fun on(day: LocalDate) = daily[day.toEpochDay()] ?: 0

    /** The last [days] days, oldest first, ending today. */
    fun lastDays(days: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> =
        (days - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to on(it) } }
}

/** Keeps the most recent block events and running counts on-device. */
class BlockLog(context: Context, private val diagnostics: DiagnosticsLog) {
    private val prefs = context.getSharedPreferences("block_log", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(BlockEvent.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableStateFlow(loadEvents())
    val events: StateFlow<List<BlockEvent>> = _events.asStateFlow()

    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<BlockStats> = _stats.asStateFlow()

    private fun loadEvents(): List<BlockEvent> =
        prefs.getString(KEY_EVENTS, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?: emptyList()

    private fun loadStats(): BlockStats =
        prefs.getString(KEY_STATS, null)?.let { runCatching { json.decodeFromString(BlockStats.serializer(), it) }.getOrNull() }
            ?: BlockStats()

    /**
     * Records a block. [reason] is shown in Recent blocks; [data] (scores, the matched word or domain,
     * the rule that fired) only goes to the diagnostics log.
     */
    @Synchronized
    fun add(kind: BlockKind, detail: String, reason: String = "", pkg: String? = null, data: Map<String, String> = emptyMap()) {
        val now = System.currentTimeMillis()
        val last = _events.value.firstOrNull()
        // Collapse repeats of the same block within a few seconds.
        if (last != null && last.kind == kind && last.detail == detail && now - last.timeMs < 5_000) return
        diagnostics.write(DiagEntry(now, "BLOCK", kind.name, pkg, detail, reason, data))
        val events = (listOf(BlockEvent(now, kind, detail, reason)) + _events.value).take(MAX_EVENTS)

        val today = dayOf(now)
        val old = _stats.value
        val daily = (old.daily + (today to (old.daily[today] ?: 0) + 1)).filterKeys { it > today - KEEP_DAYS }
        val stats = BlockStats(total = old.total + 1, daily = daily)

        _events.value = events
        _stats.value = stats
        prefs.edit()
            .putString(KEY_EVENTS, json.encodeToString(serializer, events))
            .putString(KEY_STATS, json.encodeToString(BlockStats.serializer(), stats))
            .apply()
    }

    /** Clears the event history. Counts and the chart are kept. */
    @Synchronized
    fun clear() {
        _events.value = emptyList()
        prefs.edit().remove(KEY_EVENTS).apply()
    }

    fun countToday(): Int = _stats.value.on(LocalDate.now())

    private companion object {
        const val KEY_EVENTS = "events"
        const val KEY_STATS = "stats"
        const val MAX_EVENTS = 300
        const val KEEP_DAYS = 90

        fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }
}

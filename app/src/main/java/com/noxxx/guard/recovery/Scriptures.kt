package com.noxxx.guard.recovery

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * A passage from the standard works. [struggle] marks ones chosen for hard moments (block screens,
 * urge help). Built by scripts/build-scriptures.py from public scripture text.
 */
@Serializable
data class Passage(val ref: String, val text: String, val url: String, val struggle: Boolean = false)

object Scriptures {
    const val ASSET = "scriptures.json"

    @Volatile
    var all: List<Passage> = emptyList()
        private set

    fun load(context: Context) {
        if (all.isNotEmpty()) return
        all = runCatching {
            context.assets.open(ASSET).bufferedReader().use { Json.decodeFromString<List<Passage>>(it.readText()) }
        }.getOrDefault(emptyList())
    }

    /** The same passage all day, a different one each day, cycling through the whole list. */
    fun forDay(date: LocalDate, list: List<Passage> = all): Passage? =
        list.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(date.toEpochDay(), it.size.toLong()).toInt()] }

    /** A passage for a hard moment, different from [not] when possible. */
    fun forStruggle(not: Passage? = null, list: List<Passage> = all): Passage? {
        val pool = list.filter { it.struggle }.ifEmpty { list }
        return (pool.filter { it != not }.ifEmpty { pool }).randomOrNull()
    }
}

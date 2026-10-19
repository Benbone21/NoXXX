package com.noxxx.guard.data

import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One line of the diagnostics log. [type] is BLOCK for every block, or NEAR_MISS for image scans that
 * scored high without blocking. [data] holds the specifics: model scores and thresholds, the matched
 * word or domain, the tamper rule, the detection method.
 */
@Serializable
data class DiagEntry(
    val time: Long,
    val type: String,
    val kind: String? = null,
    val pkg: String? = null,
    val app: String? = null,
    val reason: String = "",
    val data: Map<String, String> = emptyMap(),
)

/**
 * Detailed on-device record of why NoXXX blocked something, for tracking down false alarms. Unlike
 * the Recent blocks list it may contain the exact blocked word or address, so the app never shows it;
 * it only leaves the phone if you export it. JSON lines in app storage, rotated at [MAX_BYTES].
 */
class DiagnosticsLog(context: Context) {
    private val dir = context.filesDir
    val file = File(dir, "diagnostics.jsonl")
    private val old = File(dir, "diagnostics.1.jsonl")
    private val json = Json { encodeDefaults = false }
    private val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    @Synchronized
    fun write(entry: DiagEntry) {
        runCatching {
            if (file.length() > MAX_BYTES) {
                old.delete()
                file.renameTo(old)
            }
            file.appendText(json.encodeToString(DiagEntry.serializer(), entry) + "\n")
        }
    }

    /** Both log files, oldest first, behind a header describing the app and phone. */
    @Synchronized
    fun export(target: File): File {
        target.bufferedWriter().use { out ->
            out.write("# NoXXX $appVersion diagnostics · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL}\n")
            for (f in listOf(old, file)) if (f.exists()) f.bufferedReader().use { it.copyTo(out) }
        }
        return target
    }

    @Synchronized
    fun clear() {
        file.delete()
        old.delete()
    }

    private companion object {
        const val MAX_BYTES = 512 * 1024L
    }
}

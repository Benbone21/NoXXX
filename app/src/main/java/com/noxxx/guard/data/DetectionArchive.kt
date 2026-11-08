package com.noxxx.guard.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.noxxx.guard.BuildConfig
import com.noxxx.guard.policy.Sensitivity
import com.noxxx.guard.vision.Scores
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** One line of detections/index.jsonl: which saved image belongs to which scan. */
@Serializable
data class DetectionEntry(
    val time: Long,
    val type: String,
    val pkg: String,
    val scores: Map<String, String>,
    val sensitivity: String,
    val tile: String,
    val file: String,
)

/**
 * Dev builds only: keeps the exact tile the image model judged for blocks and near misses, so false
 * alarms can be pulled off the phone (scripts/pull-detections.sh) and used to tune detection. Lives in
 * private app storage (files/detections), is never shown in the app, never exported or shared, and
 * never reaches the gallery. Release builds compile every call to nothing.
 */
object DetectionArchive {
    private const val TAG = "DetectionArchive"
    private const val DIR = "detections"
    private const val INDEX = "index.jsonl"
    private const val JPEG_QUALITY = 85
    private const val MAX_IMAGES = 300
    private const val MAX_BYTES = 60L * 1024 * 1024

    /** Below the block line, scans scoring at least this explicit are still kept. */
    private const val KEEP_EXPLICIT = 0.5f

    private val json = Json { encodeDefaults = false }
    private val writer by lazy { Executors.newSingleThreadExecutor() }

    /** Whether a tile's scores make it worth saving: flagged, or close to the threshold. */
    fun worthKeeping(s: Scores, sensitivity: Sensitivity) = s.isFlagged(sensitivity) || s.explicit >= KEEP_EXPLICIT

    /**
     * Queues [pixels] (ARGB, [side] x [side]) to be written as a JPEG. Returns at once; encoding,
     * writing and pruning happen on a background thread, and a failure there never reaches the caller.
     */
    fun save(context: Context, type: String, pkg: String, top: Scores, sensitivity: Sensitivity, tile: String, pixels: IntArray?, side: Int) {
        if (!BuildConfig.DEBUG || pixels == null || side <= 0 || pixels.size != side * side) return
        val time = System.currentTimeMillis()
        val dir = File(context.filesDir, DIR)
        runCatching {
            writer.execute {
                runCatching { write(dir, time, type, pkg, top, sensitivity, tile, pixels, side) }
                    .onFailure { Log.w(TAG, "saving detection failed", it) }
            }
        }
    }

    private fun write(dir: File, time: Long, type: String, pkg: String, top: Scores, sensitivity: Sensitivity, tile: String, pixels: IntArray, side: Int) {
        dir.mkdirs()
        val name = DetectionFiles.fileName(time, type, pkg, top.explicit)
        val full = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        val long = DetectionFiles.outputSide(side)
        val bmp = if (long == side) full else Bitmap.createScaledBitmap(full, long, long, true).also { full.recycle() }
        try {
            File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            bmp.recycle()
        }
        val entry = DetectionEntry(time, type, pkg, top.toLog(), sensitivity.name, tile, name)
        val index = File(dir, INDEX)
        index.appendText(json.encodeToString(DetectionEntry.serializer(), entry) + "\n")
        prune(dir, index)
    }

    /** Drops the oldest images past the caps, and their index lines with them. */
    private fun prune(dir: File, index: File) {
        val images = dir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }.orEmpty()
            .map { DetectionFiles.Stored(it.name, it.length(), it.lastModified()) }
        val doomed = DetectionFiles.toDelete(images, MAX_IMAGES, MAX_BYTES)
        if (doomed.isEmpty()) return
        doomed.forEach { File(dir, it).delete() }
        val kept = DetectionFiles.keepIndexLines(index.readLines(), doomed.toSet())
        index.writeText(kept.joinToString("") { "$it\n" })
    }
}

/** The pure parts of [DetectionArchive]: names, sizes and what to prune. */
object DetectionFiles {
    const val MAX_SIDE = 512

    data class Stored(val name: String, val bytes: Long, val modified: Long)

    /** `<epochMs>_<BLOCK|NEAR>_<pkg>_<explicit>.jpg`, e.g. `1731000000000_NEAR_com.android.chrome_0.62.jpg`. */
    fun fileName(time: Long, type: String, pkg: String, explicit: Float): String {
        val kind = if (type == "BLOCK") "BLOCK" else "NEAR"
        val safePkg = pkg.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "unknown" }
        return "${time}_${kind}_${safePkg}_${String.format(Locale.ROOT, "%.2f", explicit)}.jpg"
    }

    /** Model input size, shrunk to at most [MAX_SIDE] should a larger model ever be bundled. */
    fun outputSide(side: Int) = minOf(side, MAX_SIDE)

    /** Names to delete, oldest first, so at most [maxCount] files and [maxBytes] remain. */
    fun toDelete(files: List<Stored>, maxCount: Int, maxBytes: Long): List<String> {
        val newestFirst = files.sortedWith(compareByDescending<Stored> { it.modified }.thenByDescending { it.name })
        var bytes = 0L
        val doomed = ArrayList<String>()
        for ((i, f) in newestFirst.withIndex()) {
            bytes += f.bytes
            if (i >= maxCount || bytes > maxBytes) doomed += f.name
        }
        return doomed.reversed()
    }

    /** Index lines whose image is still on disk. */
    fun keepIndexLines(lines: List<String>, deleted: Set<String>): List<String> =
        lines.filter { line -> line.isNotBlank() && deleted.none { line.contains("\"file\":\"$it\"") } }
}

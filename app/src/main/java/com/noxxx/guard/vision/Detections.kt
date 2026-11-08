package com.noxxx.guard.vision

import com.noxxx.guard.policy.Sensitivity
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** NudeNet 320n classes, in the model's output order (its metadata `names`). */
enum class BodyPart {
    FEMALE_GENITALIA_COVERED, FACE_FEMALE, BUTTOCKS_EXPOSED, FEMALE_BREAST_EXPOSED, FEMALE_GENITALIA_EXPOSED,
    MALE_BREAST_EXPOSED, ANUS_EXPOSED, FEET_EXPOSED, BELLY_COVERED, FEET_COVERED, ARMPITS_COVERED,
    ARMPITS_EXPOSED, FACE_MALE, BELLY_EXPOSED, MALE_GENITALIA_EXPOSED, ANUS_COVERED, FEMALE_BREAST_COVERED,
    BUTTOCKS_COVERED,
}

/** Corners in model-input pixels (0..320). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val area get() = max(0f, right - left) * max(0f, bottom - top)

    fun iou(o: Box): Float {
        val inter = max(0f, min(right, o.right) - max(left, o.left)) * max(0f, min(bottom, o.bottom) - max(top, o.top))
        val union = area + o.area - inter
        return if (union > 0f) inter / union else 0f
    }
}

data class Detection(val part: BodyPart, val score: Float, val box: Box)

/** Fitting the whole screen into the detector's square input without distortion. */
object Letterbox {
    /** Size of a [w] x [h] screen scaled so its long edge is [side]; the rest of the square is padding. */
    fun fit(w: Int, h: Int, side: Int): Pair<Int, Int> {
        val scale = side.toFloat() / max(w, h)
        return (w * scale).roundToInt().coerceIn(1, side) to (h * scale).roundToInt().coerceIn(1, side)
    }
}

/** Turns raw YOLOv8 output into detections. Pure, so it is unit-tested without a model. */
object YoloDecoder {
    /** Lowest score kept at all; also what the logs list. Same as NudeNet's own. */
    const val MIN_SCORE = 0.2f
    const val IOU = 0.45f

    /**
     * [rows] is the model's [1, 4 + classes, anchors] output without the batch: rows 0-3 are box centre x,
     * centre y, width and height, the rest are per-class scores (already sigmoid). Each anchor keeps only
     * its best class, as in NudeNet and Ultralytics.
     */
    fun decode(rows: Array<FloatArray>, minScore: Float = MIN_SCORE): List<Detection> {
        val parts = BodyPart.entries
        val classes = min(rows.size - 4, parts.size)
        if (classes <= 0) return emptyList()
        val anchors = rows[0].size
        val out = ArrayList<Detection>()
        for (a in 0 until anchors) {
            var best = 0
            var bestScore = rows[4][a]
            for (c in 1 until classes) {
                val s = rows[4 + c][a]
                if (s > bestScore) { best = c; bestScore = s }
            }
            if (bestScore < minScore) continue
            val cx = rows[0][a]; val cy = rows[1][a]; val hw = rows[2][a] / 2; val hh = rows[3][a] / 2
            out += Detection(parts[best], bestScore, Box(cx - hw, cy - hh, cx + hw, cy + hh))
        }
        return nms(out)
    }

    /**
     * Per-class non-maximum suppression, highest score first. Boxes of different classes never suppress
     * each other, so a covered-chest box can't hide an exposed one that overlaps it.
     */
    fun nms(detections: List<Detection>, iou: Float = IOU): List<Detection> {
        val kept = ArrayList<Detection>()
        for (d in detections.sortedByDescending { it.score }) {
            if (kept.none { it.part == d.part && it.box.iou(d.box) > iou }) kept += d
        }
        return kept
    }
}

/**
 * What one scan found: every detection at or above [YoloDecoder.MIN_SCORE]. Only exposed explicit parts
 * count toward a block (plus covered genitals or buttocks in Strict). Faces, male chests, bellies, feet and
 * armpits are found by the model but never flag, since they're in ordinary photos of people.
 */
class Scores(detections: List<Detection>) {
    val detections = detections.sortedByDescending { it.score }

    /** Highest score of any class, for logs. */
    val top: Detection? get() = detections.firstOrNull()

    /** Highest score among the exposed explicit parts; drives near misses and the archive. */
    val explicit: Float get() = detections.filter { it.part in EXPLICIT }.maxOfOrNull { it.score } ?: 0f

    /** The highest-scoring detection that meets its threshold at [s], if any. */
    fun trigger(s: Sensitivity): Detection? {
        val limits = THRESHOLDS.getValue(s)
        return detections.firstOrNull { d -> limits[d.part]?.let { d.score >= it } == true }
    }

    fun isFlagged(s: Sensitivity) = trigger(s) != null

    /** The rule each level applies, in words, for logs. Built from [THRESHOLDS], so it always matches. */
    fun ruleText(s: Sensitivity) = THRESHOLDS.getValue(s).entries.joinToString(", ", prefix = "any of ") { (p, t) ->
        "${p.name} ≥ ${two(t)}"
    }

    /** Top class, its confidence, the explicit score and every detection, two decimals, for the diagnostics log. */
    fun toLog(): Map<String, String> = mapOf(
        "topClass" to (top?.part?.name ?: "none"),
        "confidence" to two(top?.score ?: 0f),
        "explicit" to two(explicit),
        "detections" to detections.joinToString(", ") { "${it.part.name} ${two(it.score)}" }.ifEmpty { "none" },
    )

    companion object {
        val EXPLICIT = setOf(
            BodyPart.FEMALE_GENITALIA_EXPOSED, BodyPart.MALE_GENITALIA_EXPOSED, BodyPart.FEMALE_BREAST_EXPOSED,
            BodyPart.ANUS_EXPOSED, BodyPart.BUTTOCKS_EXPOSED,
        )

        // Tuned on real false alarms from Instagram (clothed people, faces, shirtless men) and game art,
        // whose explicit classes reach at most 0.48 (all MALE_GENITALIA_EXPOSED, on photos of men). NudeNet's
        // own explicit sample scores 0.64-0.85 on its exposed parts.
        /** Per-class confidence needed to flag a scan. Classes not listed never flag at that level. */
        val THRESHOLDS: Map<Sensitivity, Map<BodyPart, Float>> = mapOf(
            Sensitivity.RELAXED to mapOf(
                BodyPart.FEMALE_GENITALIA_EXPOSED to 0.70f, BodyPart.MALE_GENITALIA_EXPOSED to 0.70f,
                BodyPart.ANUS_EXPOSED to 0.70f,
            ),
            Sensitivity.BALANCED to mapOf(
                BodyPart.FEMALE_GENITALIA_EXPOSED to 0.55f, BodyPart.MALE_GENITALIA_EXPOSED to 0.60f,
                BodyPart.ANUS_EXPOSED to 0.55f, BodyPart.FEMALE_BREAST_EXPOSED to 0.55f,
                BodyPart.BUTTOCKS_EXPOSED to 0.60f,
            ),
            Sensitivity.STRICT to mapOf(
                BodyPart.FEMALE_GENITALIA_EXPOSED to 0.40f, BodyPart.MALE_GENITALIA_EXPOSED to 0.50f,
                BodyPart.ANUS_EXPOSED to 0.40f, BodyPart.FEMALE_BREAST_EXPOSED to 0.40f,
                BodyPart.BUTTOCKS_EXPOSED to 0.45f,
                BodyPart.FEMALE_GENITALIA_COVERED to 0.60f, BodyPart.BUTTOCKS_COVERED to 0.65f,
            ),
        )

        private fun two(f: Float) = String.format(Locale.ROOT, "%.2f", f)
    }
}

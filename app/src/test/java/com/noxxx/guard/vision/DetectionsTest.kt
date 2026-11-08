package com.noxxx.guard.vision

import com.noxxx.guard.policy.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionsTest {
    private val box = Box(10f, 10f, 50f, 50f)
    private fun d(part: BodyPart, score: Float, b: Box = box) = Detection(part, score, b)
    private fun s(vararg ds: Detection) = Scores(ds.toList())

    /** A model output with [anchors] anchors, all zero; [set] fills in one anchor's box and class score. */
    private fun output(anchors: Int, vararg set: Triple<Int, Box, Pair<BodyPart, Float>>): Array<FloatArray> {
        val rows = Array(4 + BodyPart.entries.size) { FloatArray(anchors) }
        for ((a, b, cls) in set) {
            rows[0][a] = (b.left + b.right) / 2; rows[1][a] = (b.top + b.bottom) / 2
            rows[2][a] = b.right - b.left; rows[3][a] = b.bottom - b.top
            rows[4 + cls.first.ordinal][a] = cls.second
        }
        return rows
    }

    // --- Decoding ---

    @Test fun decodesCentreBoxesToCorners() {
        val out = output(3, Triple(1, Box(100f, 40f, 140f, 120f), BodyPart.FEMALE_BREAST_EXPOSED to 0.8f))
        val dets = YoloDecoder.decode(out)
        assertEquals(1, dets.size)
        assertEquals(BodyPart.FEMALE_BREAST_EXPOSED, dets[0].part)
        assertEquals(0.8f, dets[0].score, 1e-6f)
        assertEquals(Box(100f, 40f, 140f, 120f), dets[0].box)
    }

    @Test fun eachAnchorKeepsOnlyItsBestClass() {
        val out = output(1, Triple(0, box, BodyPart.FACE_FEMALE to 0.7f))
        out[4 + BodyPart.FEMALE_BREAST_COVERED.ordinal][0] = 0.4f
        assertEquals(listOf(BodyPart.FACE_FEMALE), YoloDecoder.decode(out).map { it.part })
    }

    @Test fun dropsAnchorsBelowTheFloor() {
        val out = output(2, Triple(0, box, BodyPart.ANUS_EXPOSED to 0.19f), Triple(1, box, BodyPart.FEET_EXPOSED to 0.2f))
        assertEquals(listOf(BodyPart.FEET_EXPOSED), YoloDecoder.decode(out).map { it.part })
    }

    @Test fun malformedOutputDecodesToNothing() {
        assertTrue(YoloDecoder.decode(Array(4) { FloatArray(10) }).isEmpty())
    }

    // --- NMS ---

    @Test fun overlappingBoxesOfOneClassCollapseToTheBest() {
        val shifted = Box(12f, 12f, 52f, 52f)
        val kept = YoloDecoder.nms(listOf(d(BodyPart.BUTTOCKS_EXPOSED, 0.6f, shifted), d(BodyPart.BUTTOCKS_EXPOSED, 0.9f)))
        assertEquals(listOf(0.9f), kept.map { it.score })
    }

    @Test fun separateBoxesOfOneClassBothStay() {
        val far = Box(200f, 200f, 240f, 240f)
        assertEquals(2, YoloDecoder.nms(listOf(d(BodyPart.FEMALE_BREAST_EXPOSED, 0.8f), d(BodyPart.FEMALE_BREAST_EXPOSED, 0.7f, far))).size)
    }

    @Test fun otherClassesNeverSuppressEachOther() {
        val kept = YoloDecoder.nms(listOf(d(BodyPart.FEMALE_BREAST_COVERED, 0.9f), d(BodyPart.FEMALE_BREAST_EXPOSED, 0.6f)))
        assertEquals(2, kept.size)
    }

    @Test fun iou() {
        assertEquals(1f, box.iou(box), 1e-6f)
        assertEquals(0f, box.iou(Box(60f, 60f, 70f, 70f)), 1e-6f)
        // Half overlap: 800 / (1600 + 1600 - 800).
        assertEquals(1f / 3, box.iou(Box(30f, 10f, 70f, 50f)), 1e-6f)
    }

    @Test fun letterboxKeepsTheAspectRatio() {
        assertEquals(144 to 320, Letterbox.fit(1080, 2400, 320))
        assertEquals(320 to 144, Letterbox.fit(2400, 1080, 320))
        assertEquals(320 to 320, Letterbox.fit(224, 224, 320))
    }

    // --- The rule ---

    @Test fun clearlyExposedGenitalsFlagEveryLevel() {
        Sensitivity.entries.forEach {
            assertTrue(it.name, s(d(BodyPart.FEMALE_GENITALIA_EXPOSED, 0.9f)).isFlagged(it))
            assertTrue(it.name, s(d(BodyPart.MALE_GENITALIA_EXPOSED, 0.9f)).isFlagged(it))
            assertTrue(it.name, s(d(BodyPart.ANUS_EXPOSED, 0.9f)).isFlagged(it))
        }
    }

    @Test fun ordinaryPartsNeverFlagAtAnyLevel() {
        // Faces, men's chests, bellies, feet and armpits are in ordinary photos of people.
        val harmless = listOf(
            BodyPart.FACE_FEMALE, BodyPart.FACE_MALE, BodyPart.MALE_BREAST_EXPOSED, BodyPart.BELLY_EXPOSED,
            BodyPart.BELLY_COVERED, BodyPart.FEET_EXPOSED, BodyPart.FEET_COVERED, BodyPart.ARMPITS_EXPOSED,
            BodyPart.ARMPITS_COVERED, BodyPart.FEMALE_BREAST_COVERED, BodyPart.ANUS_COVERED,
        )
        val all = Scores(harmless.map { d(it, 1f) })
        Sensitivity.entries.forEach { assertFalse(it.name, all.isFlagged(it)) }
    }

    @Test fun coveredGenitalsAndButtocksFlagOnlyStrict() {
        for (part in listOf(BodyPart.FEMALE_GENITALIA_COVERED, BodyPart.BUTTOCKS_COVERED)) {
            assertFalse(s(d(part, 0.95f)).isFlagged(Sensitivity.RELAXED))
            assertFalse(s(d(part, 0.95f)).isFlagged(Sensitivity.BALANCED))
            assertTrue(s(d(part, 0.95f)).isFlagged(Sensitivity.STRICT))
        }
    }

    @Test fun relaxedIgnoresBreastsAndButtocks() {
        assertFalse(s(d(BodyPart.FEMALE_BREAST_EXPOSED, 0.95f), d(BodyPart.BUTTOCKS_EXPOSED, 0.95f)).isFlagged(Sensitivity.RELAXED))
        assertTrue(s(d(BodyPart.FEMALE_BREAST_EXPOSED, 0.6f)).isFlagged(Sensitivity.BALANCED))
        assertTrue(s(d(BodyPart.BUTTOCKS_EXPOSED, 0.65f)).isFlagged(Sensitivity.BALANCED))
    }

    @Test fun instagramFalseAlarmsStayBelowBalanced() {
        // The worst real false alarm: a man's photo scored as exposed male genitals at 0.46-0.48.
        val photo = s(d(BodyPart.FACE_FEMALE, 0.83f), d(BodyPart.MALE_GENITALIA_EXPOSED, 0.48f))
        assertFalse(photo.isFlagged(Sensitivity.RELAXED))
        assertFalse(photo.isFlagged(Sensitivity.BALANCED))
        assertFalse(photo.isFlagged(Sensitivity.STRICT))
    }

    @Test fun strictIsNeverLooserThanBalancedOrBalancedThanRelaxed() {
        val order = Sensitivity.entries
        for (i in 1 until order.size) {
            val looser = Scores.THRESHOLDS.getValue(order[i - 1])
            val stricter = Scores.THRESHOLDS.getValue(order[i])
            for ((part, t) in looser) {
                val u = stricter[part]
                assertTrue("$part at ${order[i]}", u != null && u <= t)
            }
        }
    }

    @Test fun triggerIsTheBestQualifyingDetection() {
        val scan = s(d(BodyPart.FACE_FEMALE, 0.95f), d(BodyPart.FEMALE_BREAST_EXPOSED, 0.6f), d(BodyPart.BUTTOCKS_EXPOSED, 0.7f))
        assertEquals(BodyPart.BUTTOCKS_EXPOSED, scan.trigger(Sensitivity.BALANCED)?.part)
        assertNull(scan.trigger(Sensitivity.RELAXED))
        assertEquals(0.7f, scan.explicit, 1e-6f)
        assertEquals(BodyPart.FACE_FEMALE, scan.top?.part)
    }

    @Test fun ruleTextNamesEveryThreshold() {
        val text = s().ruleText(Sensitivity.STRICT)
        Scores.THRESHOLDS.getValue(Sensitivity.STRICT).keys.forEach { assertTrue(it.name, it.name in text) }
        assertFalse(BodyPart.FACE_FEMALE.name in text)
    }

    @Test fun logListsTopClassAndEveryDetection() {
        val log = s(d(BodyPart.FEET_EXPOSED, 0.3f), d(BodyPart.FACE_MALE, 0.812f)).toLog()
        assertEquals("FACE_MALE", log["topClass"])
        assertEquals("0.81", log["confidence"])
        assertEquals("0.00", log["explicit"])
        assertEquals("FACE_MALE 0.81, FEET_EXPOSED 0.30", log["detections"])
        assertEquals("none", s().toLog()["topClass"])
    }
}

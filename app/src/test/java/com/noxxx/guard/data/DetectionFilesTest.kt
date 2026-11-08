package com.noxxx.guard.data

import com.noxxx.guard.data.DetectionFiles.Stored
import com.noxxx.guard.policy.Sensitivity
import com.noxxx.guard.vision.BodyPart
import com.noxxx.guard.vision.Box
import com.noxxx.guard.vision.Detection
import com.noxxx.guard.vision.Scores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionFilesTest {
    private fun stored(i: Int, bytes: Long = 100) = Stored("$i.jpg", bytes, modified = i.toLong())

    @Test fun fileNameCarriesTimeTypeAppAndScore() {
        assertEquals("1731000000000_BLOCK_com.android.chrome_0.87.jpg",
            DetectionFiles.fileName(1731000000000, "BLOCK", "com.android.chrome", 0.8749f))
        assertEquals("1731000000000_NEAR_com.android.chrome_0.50.jpg",
            DetectionFiles.fileName(1731000000000, "NEAR_MISS", "com.android.chrome", 0.5f))
    }

    @Test fun fileNameNeverEscapesTheFolder() {
        val name = DetectionFiles.fileName(1, "BLOCK", "../x/y z", 0.9f)
        assertFalse(name.contains('/'))
        assertFalse(name.contains(' '))
    }

    @Test fun outputIsModelSizeUpTo512() {
        assertEquals(224, DetectionFiles.outputSide(224))
        assertEquals(512, DetectionFiles.outputSide(1024))
    }

    @Test fun nothingPrunedUnderTheCaps() {
        assertTrue(DetectionFiles.toDelete((1..5).map { stored(it) }, maxCount = 5, maxBytes = 500).isEmpty())
    }

    @Test fun oldestGoFirstPastTheCount() {
        val files = (1..7).shuffled().map { stored(it) }
        assertEquals(listOf("1.jpg", "2.jpg"), DetectionFiles.toDelete(files, maxCount = 5, maxBytes = 10_000))
    }

    @Test fun oldestGoFirstPastTheSize() {
        // Newest first: 5, 4, 3 fit in 300 bytes; 2 and 1 don't.
        val files = (1..5).map { stored(it) }
        assertEquals(listOf("1.jpg", "2.jpg"), DetectionFiles.toDelete(files, maxCount = 300, maxBytes = 300))
    }

    @Test fun prunedImagesLeaveTheIndex() {
        val lines = listOf("""{"time":1,"file":"1.jpg"}""", """{"time":2,"file":"2.jpg"}""", "")
        assertEquals(listOf("""{"time":2,"file":"2.jpg"}"""), DetectionFiles.keepIndexLines(lines, setOf("1.jpg")))
    }

    @Test fun keepsFlaggedAndCloseCallsOnly() {
        fun s(part: BodyPart, score: Float) = Scores(listOf(Detection(part, score, Box(0f, 0f, 10f, 10f))))
        assertTrue(DetectionArchive.worthKeeping(s(BodyPart.FEMALE_BREAST_EXPOSED, 0.35f), Sensitivity.RELAXED))
        assertFalse(DetectionArchive.worthKeeping(s(BodyPart.FEMALE_BREAST_EXPOSED, 0.25f), Sensitivity.BALANCED))
        // Faces never count, however confident.
        assertFalse(DetectionArchive.worthKeeping(s(BodyPart.FACE_FEMALE, 0.99f), Sensitivity.STRICT))
        // Strict flags covered buttocks, which aren't "explicit"; that's kept too.
        assertTrue(DetectionArchive.worthKeeping(s(BodyPart.BUTTOCKS_COVERED, 0.70f), Sensitivity.STRICT))
    }
}

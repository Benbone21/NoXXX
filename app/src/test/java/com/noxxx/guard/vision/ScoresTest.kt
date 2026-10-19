package com.noxxx.guard.vision

import com.noxxx.guard.policy.Sensitivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoresTest {
    /** drawing, illustrated explicit, neutral, explicit, suggestive */
    private fun s(drawing: Float = 0f, illustrated: Float = 0f, neutral: Float = 0f, explicit: Float = 0f, suggestive: Float = 0f) =
        Scores(floatArrayOf(drawing, illustrated, neutral, explicit, suggestive))

    @Test fun clearlyExplicitIsFlaggedAtEveryLevel() {
        Sensitivity.entries.forEach { assertTrue(it.name, s(explicit = 0.95f).isFlagged(it)) }
    }

    @Test fun suggestiveAloneIsNeverFlaggedOutsideStrict() {
        // People in ordinary clothes score high on "suggestive"; that alone isn't sexual content.
        assertFalse(s(suggestive = 0.97f).isFlagged(Sensitivity.BALANCED))
        assertFalse(s(suggestive = 0.97f).isFlagged(Sensitivity.STRICT))
    }

    @Test fun borderlineScoresNoLongerFlagBalanced() {
        assertFalse(s(explicit = 0.75f).isFlagged(Sensitivity.BALANCED))
        assertTrue(s(explicit = 0.82f).isFlagged(Sensitivity.BALANCED))
    }

    @Test fun strictStillCatchesExplicitPlusSuggestive() {
        assertTrue(s(explicit = 0.40f, suggestive = 0.50f).isFlagged(Sensitivity.STRICT))
        assertFalse(s(explicit = 0.30f, suggestive = 0.45f).isFlagged(Sensitivity.STRICT))
    }

    @Test fun separateTilesAreNotAddedTogether() {
        // A cartoon in one tile and a person in another used to combine into a block neither earned.
        val tiles = listOf(s(illustrated = 0.45f, drawing = 0.5f), s(explicit = 0.25f, suggestive = 0.6f))
        assertFalse(Scores.anyFlagged(tiles, Sensitivity.STRICT))
        assertTrue(Scores.anyFlagged(tiles + s(explicit = 0.9f), Sensitivity.STRICT))
    }
}

package com.noxxx.guard.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class RecoveryTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test fun milestonesTrackTheStreak() {
        assertNull(Milestones.reached(0))
        assertEquals(1, Milestones.next(0)!!.days)
        assertEquals(7, Milestones.reached(10)!!.days)
        assertEquals(14, Milestones.next(10)!!.days)
        assertEquals(3f / 7f, Milestones.progress(10), 1e-6f)
        assertNull(Milestones.next(5000))
        assertEquals(1f, Milestones.progress(5000), 0f)
    }

    @Test fun goalStreakCountsBackFromTodayOrYesterday() {
        var s = RecoveryState()
        for (back in 1L..3L) s = s.toggle("scriptures", today.minusDays(back))
        assertEquals("today not done yet still shows the run", 3, s.streak("scriptures", today))
        s = s.toggle("scriptures", today)
        assertEquals(4, s.streak("scriptures", today))
        assertTrue(s.isDone("scriptures", today))
        s = s.toggle("scriptures", today)
        assertFalse("toggling again unchecks", s.isDone("scriptures", today))
        assertEquals(0, RecoveryState().streak("scriptures", today))
    }

    @Test fun goalsCanBeAddedAndRemoved() {
        val s = RecoveryState().addGoal("  Go to the temple ", "temple")
        assertEquals("Go to the temple", s.goals.last().title)
        assertEquals(s, s.addGoal("   ", "blank"))
        val removed = s.toggle("temple", today).removeGoal("temple")
        assertFalse(removed.goals.any { it.id == "temple" })
        assertFalse(removed.done.containsKey("temple"))
    }

    @Test fun journalKeepsNewestFirst() {
        val s = RecoveryState().addEntry(JournalEntry(1, 3, 2)).addEntry(JournalEntry(2, 4, 0, note = "Better"))
        assertEquals(listOf(2L, 1L), s.journal.map { it.timeMs })
        assertEquals(listOf(1L), s.removeEntry(2).journal.map { it.timeMs })
    }

    @Test fun dailyPassageIsStableForADayAndChangesNextDay() {
        val list = (1..5).map { Passage("Ref $it", "Text $it", "https://example.org/$it", struggle = it % 2 == 0) }
        assertEquals(Scriptures.forDay(today, list), Scriptures.forDay(today, list))
        assertNotEquals(Scriptures.forDay(today, list), Scriptures.forDay(today.plusDays(1), list))
        repeat(20) { assertTrue(Scriptures.forStruggle(list[1], list)!!.ref == "Ref 4") }
    }

    @Test fun bundledScripturesAreComplete() {
        val text = File("src/main/assets/scriptures.json").readText()
        val list = kotlinx.serialization.json.Json.decodeFromString<List<Passage>>(text)
        assertTrue(list.size >= 50)
        assertTrue(list.count { it.struggle } >= 20)
        assertTrue(list.all { it.text.isNotBlank() && it.url.startsWith("https://www.churchofjesuschrist.org/study/scriptures/") })
    }
}

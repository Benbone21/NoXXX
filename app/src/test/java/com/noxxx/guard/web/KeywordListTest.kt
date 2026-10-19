package com.noxxx.guard.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeywordListTest {
    private val list = File("src/main/assets/keywords.txt").readLines().filter { it.isNotBlank() }

    @Test fun unambiguousCoreTermsAreAlwaysListed() {
        // These also appear in ordinary text, which the generator otherwise filters out.
        val m = KeywordMatcher(list)
        for (term in listOf("porn", "hentai", "nudes", "camgirl", "milf", "redtube", "youporn", "brazzers")) {
            assertTrue("not blocked: $term", m.matches(term))
        }
    }

    @Test fun listIsSortedAndUnique() {
        assertEquals(list.sorted().distinct(), list)
    }

    @Test fun plainSearchForPornIsBlocked() {
        val m = KeywordMatcher(list)
        assertTrue(m.matches("free porn videos"))
        assertTrue(m.matches("google.com/search?q=porn"))
    }
}

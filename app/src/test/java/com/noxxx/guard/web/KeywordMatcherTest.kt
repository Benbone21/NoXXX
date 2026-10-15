package com.noxxx.guard.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Uses made-up placeholder terms; the real list ships as an asset and is checked separately below. */
class KeywordMatcherTest {
    private val m = KeywordMatcher(listOf("zorbex", "quill", "blue marlin"))

    @Test fun wholeWordsMatch() {
        assertTrue(m.matches("where to find zorbex"))
        assertTrue(m.matches("ZORBEX"))
        assertTrue(m.matches("big zorbexs online")) // plural
    }

    @Test fun wordsInsideLongerWordsNeverMatch() {
        assertFalse(m.matches("zorbexian history"))
        assertFalse(m.matches("tranquillity"))
        assertFalse(m.matches("quilling supplies"))
        assertFalse(m.matches("antizorbex"))
    }

    @Test fun shortTermsDontGetPluralMatches() {
        val short = KeywordMatcher(listOf("vex"))
        assertTrue(short.matches("vex"))
        assertFalse("plural matching needs a word of 5+ letters", short.matches("vexs"))
    }

    @Test fun phrasesMatchOnlyInOrder() {
        assertTrue(m.matches("blue marlin fishing"))
        assertFalse(m.matches("marlin blue"))
        assertFalse(m.matches("blue and marlin"))
    }

    @Test fun addressesAreDecoded() {
        assertTrue(m.matches("google.com/search?q=blue+marlin"))
        assertTrue(m.matches("example.com/search?q=blue%20marlin&x=1"))
        assertTrue(m.matches("example.com/zorbex/page"))
        assertFalse(m.matches("example.com/search?q=%ZZbroken"))
    }

    @Test fun numberForLetterSpellingsAreCaught() {
        assertTrue(m.matches("z0rb3x"))
        assertFalse("plain numbers are untouched", KeywordMatcher(listOf("ioe")).matches("103"))
    }

    @Test fun allowedWordsAreRemoved() {
        val allowing = KeywordMatcher(listOf("zorbex", "quill"), allowed = listOf("Quill"))
        assertFalse(allowing.matches("a quill pen"))
        assertTrue(allowing.matches("zorbex"))
    }

    @Test fun ordinarySearchesPassTheShippedList() {
        val asset = File("src/main/assets/keywords.txt")
        val terms = asset.readLines().filter { it.isNotBlank() }
        assertTrue("shipped list should not be empty", terms.size > 100)
        terms.forEach { assertEquals("list entries are normalized", KeywordMatcher.tokens(it).joinToString(" "), it) }

        val shipped = KeywordMatcher(terms)
        val ordinary = listOf(
            "weather tomorrow", "how to bake sourdough bread", "best hiking trails near me",
            "kotlin coroutines tutorial", "cheap flights to lisbon", "how to change a car tyre",
            "chicken curry recipe", "football scores today", "learn spanish online free",
            "symptoms of the flu", "train times london to manchester", "used bikes for sale",
            "how to fix a leaking tap", "history of the roman empire", "best budget headphones",
            "https://en.wikipedia.org/wiki/Bread", "github.com/search?q=android+accessibility",
        )
        ordinary.forEach { assertFalse("blocked an ordinary search: $it", shipped.matches(it)) }
    }
}

package com.noxxx.guard.web

import java.net.URLDecoder

/**
 * Matches blocked words and phrases against search text or addresses.
 *
 * Matching is by whole word only: the text is split into words and each word (or run of words, for
 * a phrase) is compared exactly. A blocked word inside a longer, innocent word never matches, which
 * is what makes simple "contains" filters block normal words.
 */
class KeywordMatcher(terms: Collection<String>, allowed: Collection<String> = emptyList()) {
    private val words: Set<String>
    private val phrasesByFirst: Map<String, List<List<String>>>

    init {
        val allowedForms = allowed.map { tokens(it).joinToString(" ") }.toSet()
        val forms = terms.map { tokens(it) }.filter { it.isNotEmpty() && it.joinToString(" ") !in allowedForms }
        words = forms.filter { it.size == 1 }.map { it[0] }.toSet()
        phrasesByFirst = forms.filter { it.size > 1 }.groupBy { it[0] }
    }

    val isEmpty get() = words.isEmpty() && phrasesByFirst.isEmpty()

    fun matches(text: String): Boolean {
        if (isEmpty || text.isBlank()) return false
        val toks = tokens(text)
        for (i in toks.indices) {
            val t = toks[i]
            if (t in words) return true
            // Simple plural, only for longer words so short terms can't pick up stray matches.
            if (t.length >= 5 && t.endsWith('s') && t.dropLast(1) in words) return true
            phrasesByFirst[t]?.forEach { p ->
                if (i + p.size <= toks.size && p.indices.all { toks[i + it] == p[it] }) return true
            }
        }
        return false
    }

    companion object {
        private val SPLIT = Regex("[^a-z0-9]+")
        private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't')

        /**
         * Lowercases, decodes URL escapes ("%20", "+"), splits into words, and undoes number-for-letter
         * spellings, but only inside words that mix letters and digits, so plain numbers are untouched.
         */
        fun tokens(text: String): List<String> {
            val decoded = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrElse { text.replace('+', ' ') }
            val prepared = decoded.lowercase().replace('@', 'a').replace('$', 's')
            return prepared.split(SPLIT).filter { it.isNotEmpty() }.map { tok ->
                if (tok.any { it.isDigit() } && tok.any { it.isLetter() }) {
                    tok.map { c -> LEET[c] ?: c }.joinToString("")
                } else tok
            }
        }
    }
}

package com.noxxx.guard.web

import android.content.Context
import com.noxxx.guard.policy.Policy

/** Holds the bundled word list and a matcher cached for the current settings. */
class KeywordIndex {
    @Volatile
    private var bundled: List<String> = emptyList()

    @Volatile
    private var cached: Pair<Triple<Boolean, Set<String>, Set<String>>, KeywordMatcher>? = null

    val bundledCount get() = bundled.size

    fun loadBundled(context: Context) {
        bundled = context.assets.open(ASSET).bufferedReader().readLines().filter { it.isNotBlank() }
        cached = null
    }

    fun matcherFor(p: Policy): KeywordMatcher {
        val key = Triple(p.builtInKeywords, p.customKeywords, p.allowedWords)
        cached?.let { (k, m) -> if (k == key) return m }
        val terms = (if (p.builtInKeywords) bundled else emptyList()) + p.customKeywords
        return KeywordMatcher(terms, p.allowedWords).also { cached = key to it }
    }

    companion object {
        /** Built by scripts/update-keywords.py. Not shown anywhere in the app. */
        const val ASSET = "keywords.txt"
    }
}

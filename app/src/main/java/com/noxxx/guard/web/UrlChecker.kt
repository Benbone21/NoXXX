package com.noxxx.guard.web

import com.noxxx.guard.policy.Policy

object Domains {
    private val hostChars = Regex("^[a-z0-9.-]+$")

    /** Normalizes user input like "https://www.Example.com/path" to "www.example.com", or null if invalid. */
    fun normalize(input: String): String? {
        val host = input.trim().lowercase()
            .substringAfter("://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .trimEnd('.')
        return host.takeIf { it.contains('.') && hostChars.matches(it) && !it.startsWith('.') }
    }

    /**
     * Extracts a host from browser address-bar text. Returns null when the text looks like a search
     * query rather than an address.
     */
    fun hostFromAddressBar(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        return normalize(t)
    }

    /** True if [host] or one of its parent domains is in [set]. */
    fun inSet(host: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        var h = host
        while (true) {
            if (h in set) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
        }
    }
}

sealed interface Verdict {
    /** A blocked word or phrase appeared in the text. */
    data object Keyword : Verdict

    data class Domain(val host: String) : Verdict
}

object UrlChecker {
    /** Returns why the address-bar [text] should be blocked under [policy], or null if it's fine. */
    fun check(text: String, policy: Policy, blocklist: DomainBlocklist, keywords: KeywordMatcher): Verdict? {
        val host = Domains.hostFromAddressBar(text)
        if (host != null && Domains.inSet(host, policy.allowedDomains)) return null
        if (keywords.matches(text)) return Verdict.Keyword
        if (host == null) return null
        if (Domains.inSet(host, policy.customBlockedDomains)) return Verdict.Domain(host)
        if (blocklist.matches(host)) return Verdict.Domain(host)
        return null
    }
}

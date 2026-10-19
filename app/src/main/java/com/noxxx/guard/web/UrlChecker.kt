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
    fun inSet(host: String, set: Set<String>) = matchIn(host, set) != null

    /** The entry of [set] that [host] falls under (itself or a parent domain), or null. */
    fun matchIn(host: String, set: Set<String>): String? {
        if (set.isEmpty()) return null
        var h = host
        while (true) {
            if (h in set) return h
            val i = h.indexOf('.')
            if (i < 0) return null
            h = h.substring(i + 1)
        }
    }
}

sealed interface Verdict {
    /** The blocked word or phrase [term] appeared in the text. */
    data class Keyword(val term: String) : Verdict

    /** [host] is blocked because [matched] (it or a parent domain) is listed; [custom] means the user's own list. */
    data class Domain(val host: String, val matched: String, val custom: Boolean) : Verdict
}

object UrlChecker {
    /** Returns why the address-bar [text] should be blocked under [policy], or null if it's fine. */
    fun check(text: String, policy: Policy, blocklist: DomainBlocklist, keywords: KeywordMatcher): Verdict? {
        val host = Domains.hostFromAddressBar(text)
        if (host != null && Domains.inSet(host, policy.allowedDomains)) return null
        keywords.firstMatch(text)?.let { return Verdict.Keyword(it) }
        if (host == null) return null
        Domains.matchIn(host, policy.customBlockedDomains)?.let { return Verdict.Domain(host, it, custom = true) }
        blocklist.matchingDomain(host)?.let { return Verdict.Domain(host, it, custom = false) }
        return null
    }
}

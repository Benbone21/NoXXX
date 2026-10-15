package com.noxxx.guard.web

import com.noxxx.guard.policy.Policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistTest {
    private val list = DomainBlocklist().apply {
        load("# comment\nblocked-example.com\nwww.listed-sample.org\n\nBLOCKED-EXAMPLE.com\n".byteInputStream())
    }

    @Test fun matchesDomainAndSubdomains() {
        assertEquals(3, list.size) // blocked-example.com, www.listed-sample.org, listed-sample.org
        assertEquals(3, list.domainCount) // three non-comment lines, one a case-duplicate
        assertTrue(list.matches("blocked-example.com"))
        assertTrue(list.matches("a.b.blocked-example.com"))
        assertTrue(list.matches("listed-sample.org"))
        assertTrue(list.matches("www.listed-sample.org"))
        assertFalse(list.matches("notblocked-example.com"))
        assertFalse(list.matches("com"))
    }

    @Test fun addressBarParsing() {
        assertEquals("www.example.com", Domains.hostFromAddressBar("https://www.Example.com/path?q=1"))
        assertEquals("example.com", Domains.hostFromAddressBar("example.com:8080/x"))
        assertNull(Domains.hostFromAddressBar("how to bake bread"))
        assertNull(Domains.hostFromAddressBar("localhost"))
    }

    @Test fun urlCheckerHonoursAllowAndCustomLists() {
        val p = Policy(customBlockedDomains = setOf("mine.example"), allowedDomains = setOf("ok.blocked-example.com"))
        val none = KeywordMatcher(emptyList())
        assertEquals(Verdict.Domain("blocked-example.com"), UrlChecker.check("blocked-example.com/page", p, list, none))
        assertNull(UrlChecker.check("ok.blocked-example.com", p, list, none))
        assertEquals(Verdict.Domain("sub.mine.example"), UrlChecker.check("sub.mine.example", p, list, none))
        assertNull(UrlChecker.check("wikipedia.org", p, list, none))
    }

    @Test fun keywordsMatchSearchesAsWholeWords() {
        val p = Policy(customKeywords = setOf("forbidden phrase"))
        val m = KeywordMatcher(p.customKeywords)
        assertEquals(Verdict.Keyword, UrlChecker.check("some forbidden phrase here", p, list, m))
        assertEquals(Verdict.Keyword, UrlChecker.check("google.com/search?q=forbidden+phrase", p, list, m))
        assertNull(UrlChecker.check("allowed words", p, list, m))
        assertNull(UrlChecker.check("forbiddenphrase", p, list, m))
    }
}

package com.noxxx.guard.policy

import com.noxxx.guard.web.DnsCanary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDnsTest {
    @Test fun recognisesPersonalNextDnsAndControlDHostnames() {
        assertTrue(PrivateDnsProviders.isCustom("abc123.dns.nextdns.io"))
        assertTrue(PrivateDnsProviders.isCustom("phone-abc123.dns.nextdns.io"))
        assertTrue(PrivateDnsProviders.isCustom("x1y2z3.dns.controld.com"))
        assertFalse(PrivateDnsProviders.isCustom("dns.nextdns.io"))
        assertFalse(PrivateDnsProviders.isCustom("abc.dns.nextdns.io.evil.example"))
        assertFalse(PrivateDnsProviders.isCustom("dns.google"))
        assertFalse(PrivateDnsProviders.isCustom("family.cloudflare-dns.com"))
    }

    @Test fun recognisesRethinkDnsBlocklistHostnames() {
        assertTrue(PrivateDnsProviders.isCustom("1-cbbaaiaqaaqaa.max.rethinkdns.com"))
        assertTrue(PrivateDnsProviders.isCustom("1-CBBAAIAQAAQAA.max.rethinkdns.com."))
        assertFalse("no blocklists selected", PrivateDnsProviders.isCustom("max.rethinkdns.com"))
        assertFalse(PrivateDnsProviders.isCustom("1-abc.max.rethinkdns.com.evil.example"))
        assertFalse(PrivateDnsProviders.isCustom("1-abc.evil.example.max.rethinkdns.com"))
        assertFalse(PrivateDnsProviders.isCustom("1-abc.sky.rethinkdns.com"))
    }

    @Test fun switchingToACustomFilterIsALoosening() {
        val custom = Policy().copy(privateDnsHost = "abc123.dns.nextdns.io")
        assertFalse(custom.isAtLeastAsStrictAs(Policy()))
        assertTrue("back to a built-in filter is never weaker", Policy().isAtLeastAsStrictAs(custom))
    }

    @Test fun choosingAHostWhilePrivateDnsIsNotRequiredIsNotALoosening() {
        val off = Policy().copy(requirePrivateDns = false)
        val customOff = off.copy(privateDnsHost = "1-cbbaaiaqaaqaa.max.rethinkdns.com")
        assertTrue("host has no effect while not required", customOff.isAtLeastAsStrictAs(off))
        assertTrue("then requiring it is stricter", customOff.copy(requirePrivateDns = true).isAtLeastAsStrictAs(customOff))
        assertTrue(off.copy(privateDnsHost = "abc123.dns.nextdns.io", requirePrivateDns = true).isAtLeastAsStrictAs(off))
        assertFalse("switching host while required still loosens",
            Policy().copy(privateDnsHost = "abc123.dns.nextdns.io").isAtLeastAsStrictAs(Policy()))
        assertFalse("turning the requirement off still loosens", off.isAtLeastAsStrictAs(Policy()))
    }

    @Test fun allowingSecureScreensIsALoosening() {
        val allowed = Policy().copy(secureScreenAllowedApps = setOf("com.example.vpn"))
        assertFalse(allowed.isAtLeastAsStrictAs(Policy()))
        assertTrue(Policy().isAtLeastAsStrictAs(allowed))
    }

    @Test fun canaryCountsBlockAnswersAsBlocked() {
        assertTrue(DnsCanary.isBlockedAnswer(emptyList()))          // NXDOMAIN
        assertTrue(DnsCanary.isBlockedAnswer(listOf("0.0.0.0")))
        assertTrue(DnsCanary.isBlockedAnswer(listOf("::")))
        assertTrue(DnsCanary.isBlockedAnswer(listOf("127.0.0.1")))
        assertFalse(DnsCanary.isBlockedAnswer(listOf("66.254.114.41")))
        assertFalse("one real address means it got through", DnsCanary.isBlockedAnswer(listOf("0.0.0.0", "66.254.114.41")))
    }
}

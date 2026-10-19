package com.noxxx.guard.web

import java.net.InetAddress

/**
 * Checks that a custom Private DNS filter (NextDNS / Control D) really blocks adult sites, by looking
 * up a few well-known ones through the phone's own resolver. A custom filter only counts as
 * filtering while this passes, so switching its porn blocking off in the provider's dashboard is
 * caught like turning Private DNS off.
 */
object DnsCanary {
    /** Adult sites every family filter blocks. All of them must come back blocked. */
    val CANARIES = listOf("pornhub.com", "xvideos.com", "xnxx.com")

    /** A normal site: if this doesn't resolve either, DNS is down and the result proves nothing. */
    private const val CONTROL = "example.com"

    /** [leaked] lists canaries that resolved to real addresses; [inconclusive] means DNS wasn't working. */
    data class Result(val host: String, val timeMs: Long, val leaked: List<String>, val inconclusive: Boolean) {
        val passed get() = !inconclusive && leaked.isEmpty()
    }

    @Volatile
    var last: Result? = null
        private set

    /** True when the most recent conclusive check of [host] found every canary blocked. */
    fun isVerified(host: String?) = last?.let { it.host == host && it.passed } == true

    /** A DNS answer that stops the site loading: no address (NXDOMAIN), 0.0.0.0, :: or loopback. */
    fun isBlockedAnswer(addresses: List<String>) = addresses.all { a ->
        a == "0.0.0.0" || a == "::" || a == "0:0:0:0:0:0:0:0" || a.startsWith("127.") || a == "::1"
    }

    private fun lookup(name: String): List<String>? = runCatching {
        InetAddress.getAllByName(name).mapNotNull { it.hostAddress }
    }.getOrElse { if (it is java.net.UnknownHostException) emptyList() else null }

    /** Runs the check (blocking network I/O; call off the main thread) and records the result. */
    fun check(host: String): Result {
        val control = lookup(CONTROL)
        val inconclusive = control == null || isBlockedAnswer(control)
        val leaked = if (inconclusive) emptyList() else CANARIES.filter { c -> lookup(c)?.let { !isBlockedAnswer(it) } == true }
        return Result(host, System.currentTimeMillis(), leaked, inconclusive).also { r ->
            // Keep the previous verdict when DNS is simply down.
            if (!r.inconclusive || last?.host != host) last = r
        }
    }
}

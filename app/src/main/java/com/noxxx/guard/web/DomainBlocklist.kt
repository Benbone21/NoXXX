package com.noxxx.guard.web

import android.content.Context
import java.io.InputStream

/**
 * The bundled domain list (StevenBlack hosts adult-content extension, MIT) stored as a sorted array of
 * 64-bit hashes: about 8 bytes per domain in memory, with binary-search lookups.
 */
class DomainBlocklist {
    @Volatile
    private var hashes = LongArray(0)

    /** Number of hashes held (listed domains plus their bare forms without "www."). */
    val size get() = hashes.size

    /** Number of domains in the source list. */
    @Volatile
    var domainCount = 0
        private set

    fun loadBundled(context: Context) {
        context.assets.open(ASSET).use { load(it) }
    }

    fun load(input: InputStream) {
        var buf = LongArray(100_000)
        var n = 0
        var domains = 0
        fun add(h: Long) {
            if (n == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[n++] = h
        }
        input.bufferedReader().forEachLine { raw ->
            val d = raw.trim().lowercase().trimEnd('.')
            if (d.isEmpty() || d.startsWith("#")) return@forEachLine
            domains++
            add(hash(d))
            if (d.startsWith("www.")) add(hash(d.removePrefix("www.")))
        }
        val sorted = buf.copyOf(n).also { it.sort() }
        hashes = dedupe(sorted)
        domainCount = domains
    }

    /** True if [host] or any parent domain (excluding the bare TLD) is listed. */
    fun matches(host: String) = matchingDomain(host) != null

    /** The listed domain [host] falls under (itself or a parent domain), or null. */
    fun matchingDomain(host: String): String? {
        val arr = hashes
        if (arr.isEmpty()) return null
        var h = host.lowercase().trimEnd('.')
        while (h.contains('.')) {
            if (arr.binarySearch(hash(h)) >= 0) return h
            h = h.substringAfter('.')
        }
        return null
    }

    companion object {
        // Plain text on purpose: the build tools unpack ".gz" assets and drop the extension.
        const val ASSET = "blocklist.txt"

        /** FNV-1a, 64-bit. */
        fun hash(s: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }

        private fun dedupe(sorted: LongArray): LongArray {
            if (sorted.isEmpty()) return sorted
            var w = 1
            for (i in 1 until sorted.size) if (sorted[i] != sorted[w - 1]) sorted[w++] = sorted[i]
            return sorted.copyOf(w)
        }
    }
}

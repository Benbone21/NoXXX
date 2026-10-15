package com.noxxx.guard.data

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL

/** Reads the current time from HTTPS `Date` headers so the cool-off clock can't be fooled by the device clock. */
object NetworkTime {
    private val sources = listOf(
        "https://www.google.com/generate_204",
        "https://www.cloudflare.com/cdn-cgi/trace",
    )

    /** Blocking. Returns (networkTimeMs, elapsedRealtimeAtFetch) or null. */
    fun fetch(): Pair<Long, Long>? {
        for (src in sources) {
            val conn = runCatching { URL(src).openConnection() as HttpURLConnection }.getOrNull() ?: continue
            try {
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.useCaches = false
                val start = SystemClock.elapsedRealtime()
                conn.connect()
                conn.responseCode
                val end = SystemClock.elapsedRealtime()
                val date = conn.date
                // Date has 1 s resolution; take the midpoint of the request.
                if (date > 0) return date to (start + end) / 2
            } catch (_: Exception) {
                // Try the next source.
            } finally {
                conn.disconnect()
            }
        }
        return null
    }
}

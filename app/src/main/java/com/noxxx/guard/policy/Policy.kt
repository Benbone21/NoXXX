package com.noxxx.guard.policy

import kotlinx.serialization.Serializable

@Serializable
enum class Sensitivity { RELAXED, BALANCED, STRICT }

/** Family-filter DNS-over-TLS hostnames the app accepts for Android's Private DNS setting. */
object PrivateDnsProviders {
    val all = linkedMapOf(
        "family.cloudflare-dns.com" to "Cloudflare for Families (blocks adult sites + malware)",
        "family-filter-dns.cleanbrowsing.org" to
            "CleanBrowsing Family (blocks adult sites, forces SafeSearch; can fail to load normal sites like Canvas and Okta logins)",
        "adult-filter-dns.cleanbrowsing.org" to
            "CleanBrowsing Adult (blocks adult sites; can fail to load normal sites like Canvas and Okta logins)",
    )
    // Not CleanBrowsing: in Oct 2026 its main servers answered SERVFAIL for Amazon CloudFront/S3, Okta and
    // Canvas (instructure.com) domains, so school portals and logins wouldn't load. Cloudflare resolved them.
    const val DEFAULT = "family.cloudflare-dns.com"

    fun isApproved(host: String?) = host != null && host.lowercase().trimEnd('.') in all
}

/**
 * Everything the user can configure. Changes that make protection stronger apply at once;
 * anything weaker goes through the cool-off (see [PolicyEngine]).
 */
@Serializable
data class Policy(
    val urlFilter: Boolean = true,
    val requirePrivateDns: Boolean = true,
    val privateDnsHost: String = PrivateDnsProviders.DEFAULT,
    val customBlockedDomains: Set<String> = emptySet(),
    val allowedDomains: Set<String> = emptySet(),
    val customKeywords: Set<String> = emptySet(),
    /** Use the bundled list of blocked words (whole-word matching). */
    val builtInKeywords: Boolean = true,
    /** Words never treated as blocked, for false positives. */
    val allowedWords: Set<String> = emptySet(),
    /** Also check search boxes in apps other than browsers. */
    val checkSearchBoxes: Boolean = true,
    val blockedApps: Set<String> = emptySet(),
    /** Apps that can never be blocked (banking, password managers, maps, etc.). Adding one is a loosening. */
    val neverBlockApps: Set<String> = emptySet(),
    val blockUnsupportedBrowsers: Boolean = true,
    val blockPrivateBrowsing: Boolean = true,
    val imageScan: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.BALANCED,
    val imageScanExemptApps: Set<String> = emptySet(),
    /** Also run image detection in messaging and video-call apps (off by default; they false-alarm on your own camera). */
    val scanMessagingApps: Boolean = false,
    val tamperProtection: Boolean = true,
    val coolOffMinutes: Int = 24 * 60,
) {
    fun isAtLeastAsStrictAs(old: Policy): Boolean = weakeningsFrom(old).isEmpty()

    /** Human-readable list of every way this policy is weaker than [old]. Empty means not weaker. */
    fun weakeningsFrom(old: Policy): List<String> = buildList {
        fun flag(now: Boolean, before: Boolean, label: String) {
            if (before && !now) add("Turn off $label")
        }
        flag(urlFilter, old.urlFilter, "website blocking")
        flag(builtInKeywords, old.builtInKeywords, "the built-in word list")
        flag(checkSearchBoxes, old.checkSearchBoxes, "search-box checking")
        flag(requirePrivateDns, old.requirePrivateDns, "the Private DNS requirement")
        flag(blockUnsupportedBrowsers, old.blockUnsupportedBrowsers, "unsupported-browser blocking")
        flag(blockPrivateBrowsing, old.blockPrivateBrowsing, "private-browsing blocking")
        flag(imageScan, old.imageScan, "image detection")
        flag(scanMessagingApps, old.scanMessagingApps, "image detection in messaging apps")
        flag(tamperProtection, old.tamperProtection, "tamper protection")

        if (privateDnsHost != old.privateDnsHost && !PrivateDnsProviders.isApproved(privateDnsHost)) {
            add("Use unapproved Private DNS host $privateDnsHost")
        }
        (old.customBlockedDomains - customBlockedDomains).takeIf { it.isNotEmpty() }
            ?.let { add("Unblock ${it.size} custom domain(s): ${it.joinToString()}") }
        (allowedDomains - old.allowedDomains).takeIf { it.isNotEmpty() }
            ?.let { add("Allow ${it.size} domain(s): ${it.joinToString()}") }
        (old.customKeywords - customKeywords).takeIf { it.isNotEmpty() }
            ?.let { add("Remove ${it.size} keyword(s)") }
        (allowedWords - old.allowedWords).takeIf { it.isNotEmpty() }
            ?.let { add("Allow ${it.size} word(s)") }
        (old.blockedApps - blockedApps).takeIf { it.isNotEmpty() }
            ?.let { add("Unblock ${it.size} app(s)") }
        (neverBlockApps - old.neverBlockApps).takeIf { it.isNotEmpty() }
            ?.let { add("Add ${it.size} app(s) to the never-block list") }
        (imageScanExemptApps - old.imageScanExemptApps).takeIf { it.isNotEmpty() }
            ?.let { add("Skip image detection in ${it.size} app(s)") }
        if (sensitivity < old.sensitivity) {
            add("Lower image sensitivity to ${sensitivity.name.lowercase()}")
        }
        if (coolOffMinutes < old.coolOffMinutes) {
            add("Shorten cool-off to ${formatMinutes(coolOffMinutes)}")
        }
    }
}

/**
 * A request to unlock settings. It waits out the cool-off, then stays ready for [READY_MS] until the
 * user opens it; once opened, settings stay editable for [WINDOW_MS] and then lock again.
 * All times are counted by [ClockState] (monotonic), not the wall clock.
 */
@Serializable
data class UnlockRequest(
    val requiredMs: Long,
    val elapsedMs: Long = 0,
    /** Value of [elapsedMs] when the edit window was opened, or null while waiting/ready. */
    val openedAtMs: Long? = null,
) {
    val remainingWaitMs get() = (requiredMs - elapsedMs).coerceAtLeast(0)
    val isWaiting get() = openedAtMs == null && elapsedMs < requiredMs
    val isReady get() = openedAtMs == null && elapsedMs >= requiredMs
    val isOpen get() = openedAtMs != null && elapsedMs < openedAtMs + WINDOW_MS
    val windowRemainingMs get() = openedAtMs?.let { (it + WINDOW_MS - elapsedMs).coerceAtLeast(0) } ?: 0
    val readyRemainingMs get() = (requiredMs + READY_MS - elapsedMs).coerceAtLeast(0)
    val isExpired
        get() = if (openedAtMs != null) elapsedMs >= openedAtMs + WINDOW_MS else elapsedMs >= requiredMs + READY_MS

    companion object {
        /** How long settings stay editable once an unlock is opened. */
        const val WINDOW_MS = 15 * 60_000L
        /** How long a finished cool-off waits for the user to open it. */
        const val READY_MS = 15 * 60_000L
    }
}

@Serializable
data class TamperEvent(val timeMs: Long, val reason: String)

@Serializable
data class GuardState(
    /** False during first-time setup: every change applies at once and nothing is locked. */
    val committed: Boolean = false,
    /** True once the first-run setup guide has been completed or skipped. */
    val onboardingDone: Boolean = false,
    val policy: Policy = Policy(),
    /** Regular unlock: allows editing NoXXX settings (TamperShield stays active). */
    val unlock: UnlockRequest? = null,
    /** Full unlock: allows deactivating device admin / accessibility (TamperShield off). */
    val fullUnlock: UnlockRequest? = null,
    val lockdownRemainingMs: Long = 0,
    val partnerHash: String? = null,
    val pinHash: String? = null,
    val streakStartWallMs: Long = 0,
    val bestStreakMs: Long = 0,
    val clock: ClockState = ClockState(),
    /** Recent tamper/disabling events, newest first. Persists across sessions. */
    val tamperEvents: List<TamperEvent> = emptyList(),
    val tamperCount: Int = 0,
) {
    /** Settings can be loosened only during setup or inside an open unlock window. */
    val settingsEditable get() = !committed || unlock?.isOpen == true
    /** TamperShield is off only during an open full-unlock window. */
    val tamperShieldOff get() = !committed || fullUnlock?.isOpen == true
}

fun formatMinutes(minutes: Int): String {
    fun count(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"
    return when {
        minutes % (24 * 60) == 0 -> count(minutes / (24 * 60), "day")
        minutes % 60 == 0 -> count(minutes / 60, "hour")
        else -> "$minutes min"
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = (ms + 59_999) / 60_000
    val d = totalMin / (24 * 60)
    val h = (totalMin % (24 * 60)) / 60
    val m = totalMin % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

package com.noxxx.guard.monitor

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Knows which installed apps are web browsers and which ones NoXXX can read the address bar of. */
class BrowserRegistry(private val context: Context) {
    @Volatile
    private var installed: Set<String> = emptySet()

    init {
        refresh()
    }

    fun refresh() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        @Suppress("DEPRECATION")
        val found = context.packageManager
            .queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
        installed = found - context.packageName
    }

    fun installedBrowsers(): Set<String> = installed

    fun isBrowser(pkg: String) = pkg in installed || pkg in SUPPORTED

    fun isSupported(pkg: String) = pkg in SUPPORTED

    fun addressBarIds(pkg: String) = ADDRESS_BAR_IDS.map { "$pkg:id/$it" }

    companion object {
        /** Browsers whose address bar NoXXX reads. Others can be blocked outright. */
        val SUPPORTED = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
            "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.opera.browser",
        )

        /** View ids of address bars across Chromium, Firefox, Samsung and DuckDuckGo builds. */
        private val ADDRESS_BAR_IDS = listOf(
            "url_bar",
            "url_field",
            "search_box_text",
            "mozac_browser_toolbar_url_view",
            "mozac_browser_toolbar_edit_url_view",
            "display_url",
            "location_bar_edit_text",
            "custom_tab_toolbar_url_bar_text",
            "omnibarTextInput",
        )
    }
}

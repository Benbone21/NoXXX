package com.noxxx.guard.monitor

/** Apps the user chose for NoXXX not to look inside. Browsers and guarded system screens can never be ignored. */
object IgnoredApps {
    fun isIgnored(pkg: String, ignored: Set<String>, isBrowser: Boolean) =
        pkg in ignored && !isBrowser && pkg !in TamperGuard.GUARDED_PACKAGES

    /** Packages the picker shows as always checked. */
    fun neverIgnorable(browsers: Set<String>) = browsers + TamperGuard.GUARDED_PACKAGES
}

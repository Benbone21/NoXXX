package com.noxxx.guard.monitor

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Apps NoXXX will never block, so protection can't lock you out of something you depend on. Built-in
 * essentials (phone, texts for 2FA codes, settings, clock, contacts, the store, the launcher and
 * keyboard) are always protected and need no setup. The user's own never-block list is layered on
 * top for banking, password managers, maps, and the like.
 */
object EssentialApps {
    /** Core system apps across AOSP, Google and Samsung builds. */
    private val BUILT_IN = setOf(
        "com.android.settings",
        // Phone / dialer
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.phone", "com.android.server.telecom", "com.android.incallui",
        // Texts (2FA codes arrive here)
        "com.android.mms", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        // Clock / alarms
        "com.android.deskclock", "com.google.android.deskclock", "com.sec.android.app.clockpackage",
        // Contacts
        "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts",
        // App stores (to reinstall or update)
        "com.android.vending", "com.sec.android.app.samsungapps",
        // Package installer (so you can install and uninstall apps)
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.samsung.android.packageinstaller",
    )

    /**
     * Commonly-depended-on apps protected out of the box if installed. Password managers and
     * authenticators are here because they gate access to banking, email and 2FA; actual bank apps
     * vary by region, so those go in the user's own never-block list.
     */
    private val RECOMMENDED = setOf(
        // Password managers / authenticators
        "com.x8bit.bitwarden", "com.bitwarden.authenticator",
        "com.google.android.apps.authenticator2", "com.azure.authenticator",
        "com.beemdevelopment.aegis", "proton.android.pass", "com.agilebits.onepassword",
        // Maps / navigation
        "app.comaps.fdroid", "app.comaps.google", "app.vela",
        "app.organicmaps", "app.organicmaps.web",
        // Scripture / church
        "org.lds.ldssa",
    )

    /** The full protected set for this device: essentials, recommended apps, and the live system defaults. */
    fun protectedSet(context: Context): Set<String> = buildSet {
        addAll(BUILT_IN)
        addAll(RECOMMENDED)
        add(context.packageName)
        add("android")
        add("com.android.systemui")
        @Suppress("DEPRECATION")
        runCatching {
            context.packageManager
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .forEach { add(it.activityInfo.packageName) }
        }
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(::add) }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context)?.let(::add) }
        runCatching {
            context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { add(it.packageName) }
        }
    }
}

package com.noxxx.guard.web

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.provider.Settings
import com.noxxx.guard.policy.PrivateDnsProviders

/**
 * Android's built-in Private DNS (DNS-over-TLS, Android 9+), pointed at a family-filter resolver.
 * It filters every app system-wide without a VPN, so a separate VPN app can keep running.
 */
object PrivateDns {
    data class Status(val online: Boolean, val active: Boolean, val host: String?) {
        val filtering get() = active && PrivateDnsProviders.isApproved(host)
    }

    fun status(context: Context): Status {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return Status(online = false, active = false, host = null)
        val lp = cm.getLinkProperties(net) ?: return Status(online = true, active = false, host = null)
        return Status(online = true, active = lp.isPrivateDnsActive, host = lp.privateDnsServerName)
    }

    /** Offline counts as OK: with no network there is nothing to filter. */
    fun isCompliant(context: Context) = status(context).let { !it.online || it.filtering }

    /** Granted once with: adb shell pm grant com.noxxx.guard android.permission.WRITE_SECURE_SETTINGS */
    fun canEnforce(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Sets Private DNS to [host] when the adb grant is present. Returns true if it was written. */
    fun enforce(context: Context, host: String): Boolean {
        if (!canEnforce(context)) return false
        val cr = context.contentResolver
        // Reading hidden keys can throw on newer Android; if so, just write.
        val modeOk = runCatching { Settings.Global.getString(cr, KEY_MODE) == MODE_HOSTNAME }.getOrDefault(false)
        val hostOk = runCatching { Settings.Global.getString(cr, KEY_SPECIFIER) == host }.getOrDefault(false)
        return runCatching {
            if (!modeOk || !hostOk) {
                Settings.Global.putString(cr, KEY_SPECIFIER, host)
                Settings.Global.putString(cr, KEY_MODE, MODE_HOSTNAME)
            }
            true
        }.getOrDefault(false)
    }

    private const val KEY_MODE = "private_dns_mode"
    private const val KEY_SPECIFIER = "private_dns_specifier"
    private const val MODE_HOSTNAME = "hostname"
}

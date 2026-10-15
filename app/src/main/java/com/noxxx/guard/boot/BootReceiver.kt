package com.noxxx.guard.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noxxx.guard.NoXXXApp
import com.noxxx.guard.web.PrivateDns

/** Re-applies Private DNS after a reboot or update. The accessibility service restarts on its own. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as NoXXXApp
        app.store.tick()
        val s = app.store.state.value
        if (!s.settingsEditable && s.policy.requirePrivateDns) PrivateDns.enforce(context, s.policy.privateDnsHost)
    }
}

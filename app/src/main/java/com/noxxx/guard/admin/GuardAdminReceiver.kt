package com.noxxx.guard.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.noxxx.guard.R

/** While active, Android refuses to uninstall NoXXX until this is switched off first. */
class GuardAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    companion object {
        fun component(context: Context) = ComponentName(context, GuardAdminReceiver::class.java)

        fun isActive(context: Context) =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        fun activationIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_description))
    }
}

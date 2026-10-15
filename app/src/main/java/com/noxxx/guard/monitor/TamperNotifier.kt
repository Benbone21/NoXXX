package com.noxxx.guard.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.noxxx.guard.R
import com.noxxx.guard.ui.MainActivity

object TamperNotifier {
    private const val CHANNEL_ID = "tamper_alert"
    private const val NOTIFICATION_ID = 9001

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Tamper alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when protection is disabled"
        }
        nm.createNotificationChannel(channel)
    }

    fun showGuardDown(context: Context) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Protection is off")
            .setContentText("The guard service was disabled. Your streak has been reset. Tap to re-enable.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("The guard service was disabled. Your streak has been reset and the event was recorded. Tap to open NoXXX and re-enable the guard service."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pi)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID)
    }
}

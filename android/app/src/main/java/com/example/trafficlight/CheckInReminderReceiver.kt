package com.example.trafficlight

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class CheckInReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(notificationManager)
        CheckInAlarmScheduler.schedule(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        notificationManager.notify(NOTIFICATION_ID, buildNotification(context))
    }

    private fun buildNotification(context: Context): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROMPT_MODE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_traffic_light)
            .setContentTitle("What's up?")
            .setContentText("Green, yellow, or red?")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Check-ins",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "check_ins"
        private const val NOTIFICATION_ID = 100
    }
}

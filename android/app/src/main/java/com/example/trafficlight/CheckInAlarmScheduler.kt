package com.example.trafficlight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object CheckInAlarmScheduler {
    private const val REQUEST_CODE = 50

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = reminderIntent(context)
        alarmManager.cancel(intent)

        val settings = ReminderSettingsStore(context).load()
        if (settings.mode == ReminderMode.OFF) return

        val nextTrigger = nextTriggerMillis(settings) ?: return
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, intent)
    }

    private fun nextTriggerMillis(settings: ReminderSettings): Long? {
        val now = LocalDateTime.now()
        val next = when (settings.mode) {
            ReminderMode.INTERVAL -> nextIntervalTime(now, settings)
            ReminderMode.FIXED_TIMES -> nextFixedTime(now, settings.fixedHours)
            ReminderMode.OFF -> null
        }

        return next
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
    }

    private fun nextIntervalTime(now: LocalDateTime, settings: ReminderSettings): LocalDateTime {
        val todayStart = now.toLocalDate().atTime(settings.startHour, 0)
        val todayEnd = now.toLocalDate().atTime(settings.endHour, 0)

        if (now.isBefore(todayStart)) return todayStart
        if (!now.isBefore(todayEnd)) return now.toLocalDate().plusDays(1).atTime(settings.startHour, 0)

        var candidate = todayStart
        while (!candidate.isAfter(now)) {
            candidate = candidate.plusMinutes(settings.intervalMinutes.toLong())
        }

        return if (candidate.isBefore(todayEnd) || candidate == todayEnd) {
            candidate
        } else {
            now.toLocalDate().plusDays(1).atTime(settings.startHour, 0)
        }
    }

    private fun nextFixedTime(now: LocalDateTime, fixedHours: List<Int>): LocalDateTime {
        val sortedHours = fixedHours.distinct().sorted()
        val today = now.toLocalDate()
        val todayCandidate = sortedHours
            .map { today.atTime(it, 0) }
            .firstOrNull { it.isAfter(now) }

        return todayCandidate ?: LocalDate.now().plusDays(1).atTime(sortedHours.first(), 0)
    }

    private fun reminderIntent(context: Context): PendingIntent {
        val intent = Intent(context, CheckInReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

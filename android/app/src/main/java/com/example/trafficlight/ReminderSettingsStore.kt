package com.example.trafficlight

import android.content.Context

class ReminderSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)

    fun load(): ReminderSettings {
        val mode = ReminderMode.entries.firstOrNull {
            it.name == preferences.getString(KEY_MODE, ReminderMode.INTERVAL.name)
        } ?: ReminderMode.INTERVAL

        return ReminderSettings(
            mode = mode,
            intervalMinutes = preferences.getInt(KEY_INTERVAL_MINUTES, 60),
            startMinuteOfDay = preferences.getInt(
                KEY_START_MINUTE_OF_DAY,
                preferences.getInt(KEY_START_HOUR, 9) * 60,
            ),
            endMinuteOfDay = preferences.getInt(
                KEY_END_MINUTE_OF_DAY,
                preferences.getInt(KEY_END_HOUR, 21) * 60,
            ),
            pausedUntilMillis = preferences.getLong(KEY_PAUSED_UNTIL_MILLIS, 0L),
            fixedHours = preferences.getString(KEY_FIXED_HOURS, null)
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?.filter { it in 0..23 }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(9, 13, 17, 21),
        )
    }

    fun save(settings: ReminderSettings) {
        preferences.edit()
            .putString(KEY_MODE, settings.mode.name)
            .putInt(KEY_INTERVAL_MINUTES, settings.intervalMinutes)
            .putInt(KEY_START_MINUTE_OF_DAY, settings.startMinuteOfDay)
            .putInt(KEY_END_MINUTE_OF_DAY, settings.endMinuteOfDay)
            .putLong(KEY_PAUSED_UNTIL_MILLIS, settings.pausedUntilMillis)
            .putString(KEY_FIXED_HOURS, settings.fixedHours.joinToString(","))
            .apply()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_START_HOUR = "start_hour"
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_START_MINUTE_OF_DAY = "start_minute_of_day"
        private const val KEY_END_MINUTE_OF_DAY = "end_minute_of_day"
        private const val KEY_PAUSED_UNTIL_MILLIS = "paused_until_millis"
        private const val KEY_FIXED_HOURS = "fixed_hours"
    }
}

data class ReminderSettings(
    val mode: ReminderMode,
    val intervalMinutes: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val pausedUntilMillis: Long = 0L,
    val fixedHours: List<Int>,
)

enum class ReminderMode {
    INTERVAL,
    FIXED_TIMES,
    OFF,
}

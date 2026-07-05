package com.example.trafficlight

import android.content.Context

class MoodEntryStore(context: Context) {
    private val preferences = context.getSharedPreferences("mood_entries", Context.MODE_PRIVATE)

    fun save(color: MoodColor, timestampMillis: Long = System.currentTimeMillis()) {
        val existing = preferences.getString(KEY_ENTRIES, "").orEmpty()
        val entry = "$timestampMillis|${color.storedValue}"
        val nextValue = if (existing.isBlank()) entry else "$existing\n$entry"
        preferences.edit().putString(KEY_ENTRIES, nextValue).apply()
    }

    fun all(): List<MoodEntry> {
        return preferences.getString(KEY_ENTRIES, "")
            .orEmpty()
            .lineSequence()
            .mapNotNull(::parseEntry)
            .toList()
    }

    private fun parseEntry(line: String): MoodEntry? {
        val parts = line.split('|')
        if (parts.size != 2) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val color = MoodColor.entries.firstOrNull { it.storedValue == parts[1] } ?: return null
        return MoodEntry(timestamp, color)
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
    }
}

data class MoodEntry(
    val timestampMillis: Long,
    val color: MoodColor,
)

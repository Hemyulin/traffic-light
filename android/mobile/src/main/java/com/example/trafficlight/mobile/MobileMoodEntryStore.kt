package com.example.trafficlight.mobile

import android.content.Context

class MobileMoodEntryStore(context: Context) {
    private val preferences = context.getSharedPreferences("mood_entries", Context.MODE_PRIVATE)

    fun save(entry: MoodEntry) {
        val entries = all().associateBy { it.timestampMillis }.toMutableMap()
        entries[entry.timestampMillis] = entry
        val serialized = entries.values
            .sortedBy { it.timestampMillis }
            .joinToString("\n") { "${it.timestampMillis}|${it.color.storedValue}" }
        preferences.edit().putString(KEY_ENTRIES, serialized).apply()
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

enum class MoodColor(val storedValue: String) {
    GREEN("green"),
    YELLOW("yellow"),
    RED("red"),
}

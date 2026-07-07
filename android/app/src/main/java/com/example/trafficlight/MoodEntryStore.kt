package com.example.trafficlight

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder

class MoodEntryStore(context: Context) {
    private val preferences = context.getSharedPreferences("mood_entries", Context.MODE_PRIVATE)

    fun save(
        color: MoodColor,
        timestampMillis: Long = System.currentTimeMillis(),
        isConflict: Boolean = false,
        note: String = "",
        tags: List<String> = emptyList(),
    ): MoodEntry {
        val moodEntry = MoodEntry(timestampMillis, color, isConflict, note, tags)
        val existing = preferences.getString(KEY_ENTRIES, "").orEmpty()
        val entry = moodEntry.serialize()
        val nextValue = if (existing.isBlank()) entry else "$existing\n$entry"
        preferences.edit().putString(KEY_ENTRIES, nextValue).apply()
        return moodEntry
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
        if (parts.size < 2) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val color = MoodColor.entries.firstOrNull { it.storedValue == parts[1] } ?: return null
        val isConflict = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
        val note = parts.getOrNull(3)?.decodeField().orEmpty()
        val tags = parts.getOrNull(4)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.map { it.decodeField() }
            .orEmpty()
        return MoodEntry(timestamp, color, isConflict, note, tags)
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
    }
}

data class MoodEntry(
    val timestampMillis: Long,
    val color: MoodColor,
    val isConflict: Boolean = false,
    val note: String = "",
    val tags: List<String> = emptyList(),
)

private fun MoodEntry.serialize(): String {
    return listOf(
        timestampMillis.toString(),
        color.storedValue,
        isConflict.toString(),
        note.encodeField(),
        tags.joinToString(",") { it.encodeField() },
    ).joinToString("|")
}

private fun String.encodeField(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.decodeField(): String = URLDecoder.decode(this, Charsets.UTF_8.name())

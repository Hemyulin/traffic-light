package com.example.trafficlight.mobile

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder

class MobileMoodEntryStore(context: Context) {
    private val preferences = context.getSharedPreferences("mood_entries", Context.MODE_PRIVATE)

    fun save(entry: MoodEntry) {
        val entries = all().associateBy { it.timestampMillis }.toMutableMap()
        val existing = entries[entry.timestampMillis]
        entries[entry.timestampMillis] = if (existing == null) {
            entry
        } else {
            entry.copy(
                note = existing.note.ifBlank { entry.note },
                tags = (existing.tags + entry.tags).distinct(),
                isConflict = existing.isConflict || entry.isConflict,
            )
        }
        saveAll(entries.values)
    }

    fun update(entry: MoodEntry) {
        val entries = all().associateBy { it.timestampMillis }.toMutableMap()
        entries[entry.timestampMillis] = entry
        saveAll(entries.values)
    }

    fun delete(timestampMillis: Long) {
        saveAll(all().filterNot { it.timestampMillis == timestampMillis })
    }

    fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    fun csv(): String {
        return buildString {
            appendLine("timestamp,color,conflict,note,tags")
            all().forEach { entry ->
                appendLine("${entry.timestampMillis},${entry.color.storedValue},${entry.isConflict},${entry.note.csvEscape()},${entry.tags.joinToString(";").csvEscape()}")
            }
        }
    }

    private fun saveAll(entries: Collection<MoodEntry>) {
        val serialized = entries
            .sortedBy { it.timestampMillis }
            .joinToString("\n") { it.serialize() }
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

enum class MoodColor(val storedValue: String) {
    GREEN("green"),
    YELLOW("yellow"),
    RED("red"),
}

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

private fun String.csvEscape(): String = "\"${replace("\"", "\"\"")}\""

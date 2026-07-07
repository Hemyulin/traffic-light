package com.example.trafficlight

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object MoodSyncPublisher {
    private const val PATH_PREFIX = "/mood_entries"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_COLOR = "color"
    private const val KEY_CONFLICT = "conflict"
    private const val KEY_NOTE = "note"
    private const val KEY_TAGS = "tags"

    fun publishAll(context: Context, entries: List<MoodEntry>) {
        entries.forEach { entry ->
            publish(context, entry)
        }
    }

    fun publish(context: Context, entry: MoodEntry) {
        val request = PutDataMapRequest.create("$PATH_PREFIX/${entry.timestampMillis}").apply {
            dataMap.putLong(KEY_TIMESTAMP, entry.timestampMillis)
            dataMap.putString(KEY_COLOR, entry.color.storedValue)
            dataMap.putBoolean(KEY_CONFLICT, entry.isConflict)
            dataMap.putString(KEY_NOTE, entry.note)
            dataMap.putStringArrayList(KEY_TAGS, ArrayList(entry.tags))
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
    }
}

package com.example.trafficlight

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object MoodSyncPublisher {
    private const val PATH_PREFIX = "/mood_entries"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_COLOR = "color"

    fun publishAll(context: Context, entries: List<MoodEntry>) {
        entries.forEach { entry ->
            publish(context, entry)
        }
    }

    fun publish(context: Context, entry: MoodEntry) {
        val request = PutDataMapRequest.create("$PATH_PREFIX/${entry.timestampMillis}").apply {
            dataMap.putLong(KEY_TIMESTAMP, entry.timestampMillis)
            dataMap.putString(KEY_COLOR, entry.color.storedValue)
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
    }
}

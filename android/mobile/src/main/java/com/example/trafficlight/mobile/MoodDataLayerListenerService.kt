package com.example.trafficlight.mobile

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class MoodDataLayerListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val store = MobileMoodEntryStore(this)

        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (!item.uri.path.orEmpty().startsWith(PATH_PREFIX)) continue

            val dataMap = DataMapItem.fromDataItem(item).dataMap
            val timestamp = dataMap.getLong(KEY_TIMESTAMP)
            val colorValue = dataMap.getString(KEY_COLOR).orEmpty()
            val color = MoodColor.entries.firstOrNull { it.storedValue == colorValue } ?: continue
            store.save(MoodEntry(timestamp, color))
        }
    }

    companion object {
        private const val PATH_PREFIX = "/mood_entries"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_COLOR = "color"
    }
}

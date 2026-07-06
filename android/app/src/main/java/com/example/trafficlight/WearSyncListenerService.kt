package com.example.trafficlight

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearSyncListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_SYNC_REQUEST) {
            MoodSyncPublisher.publishAll(this, MoodEntryStore(this).all())
        }
    }

    companion object {
        private const val PATH_SYNC_REQUEST = "/sync_request"
    }
}

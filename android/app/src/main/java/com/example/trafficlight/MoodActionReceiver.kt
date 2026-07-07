package com.example.trafficlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator

class MoodActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val colorValue = intent?.getStringExtra(EXTRA_COLOR).orEmpty()
        val color = MoodColor.entries.firstOrNull { it.storedValue == colorValue } ?: return
        val entry = MoodEntryStore(context).save(color)
        MoodSyncPublisher.publish(context, entry)
        CheckInAlarmScheduler.schedule(context)
        context.getSystemService(Vibrator::class.java)
            .vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    companion object {
        const val EXTRA_COLOR = "com.example.trafficlight.COLOR"
    }
}

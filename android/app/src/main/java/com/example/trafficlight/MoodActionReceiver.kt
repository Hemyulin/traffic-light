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
        val effect = when (color) {
            MoodColor.GREEN -> VibrationEffect.EFFECT_TICK
            MoodColor.YELLOW -> VibrationEffect.EFFECT_CLICK
            MoodColor.RED -> VibrationEffect.EFFECT_HEAVY_CLICK
        }
        context.getSystemService(Vibrator::class.java)
            .vibrate(VibrationEffect.createPredefined(effect))
    }

    companion object {
        const val EXTRA_COLOR = "com.example.trafficlight.COLOR"
    }
}

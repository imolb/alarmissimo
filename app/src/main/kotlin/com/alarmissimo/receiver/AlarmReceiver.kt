package com.alarmissimo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alarmissimo.service.AlarmForegroundService

/**
 * Receives alarm fire intents from [android.app.AlarmManager] and starts the
 * [AlarmForegroundService] to handle playback.
 *
 * BroadcastReceiver.goAsync() is capped at roughly 10 seconds on Android 8+, which is too short
 * for TTS initialisation + speech.  Delegating to a foreground service keeps the process alive
 * for the full playback duration and satisfies Android's background-execution restrictions.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_FIRE    = "com.alarmissimo.ACTION_ALARM_FIRE"
        const val EXTRA_ALARM_SET_ID   = "alarm_set_id"
        const val EXTRA_ALARM_EVENT_ID = "alarm_event_id"
        const val EXTRA_ALARM_MESSAGE  = "alarm_message"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_FIRE) return

        val alarmSetId   = intent.getLongExtra(EXTRA_ALARM_SET_ID, -1L)
        val alarmEventId = intent.getLongExtra(EXTRA_ALARM_EVENT_ID, -1L)
        val message      = intent.getStringExtra(EXTRA_ALARM_MESSAGE) ?: ""
        if (alarmSetId == -1L || alarmEventId == -1L) return

        val serviceIntent = AlarmForegroundService.buildIntent(context, alarmSetId, alarmEventId, message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

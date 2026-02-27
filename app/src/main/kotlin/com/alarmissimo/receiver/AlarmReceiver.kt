package com.alarmissimo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmissimo.AlarmissimoApp
import com.alarmissimo.service.AlarmPlaybackHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives alarm fire intents from [AlarmManager] and triggers audio playback.
 *
 * After playback, the alarm is automatically rescheduled for its next occurrence.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_FIRE = "com.alarmissimo.ACTION_ALARM_FIRE"
        const val EXTRA_ALARM_SET_ID = "alarm_set_id"
        const val EXTRA_ALARM_EVENT_ID = "alarm_event_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_FIRE) return

        val alarmSetId = intent.getLongExtra(EXTRA_ALARM_SET_ID, -1L)
        val alarmEventId = intent.getLongExtra(EXTRA_ALARM_EVENT_ID, -1L)
        if (alarmSetId == -1L || alarmEventId == -1L) return

        val app = context.applicationContext as AlarmissimoApp
        val repository = app.repository

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmSets = repository.getAlarmSets()
                val alarmSet = alarmSets.find { it.id == alarmSetId } ?: return@launch
                val alarmEvent = alarmSet.alarmEvents.find { it.id == alarmEventId } ?: return@launch

                // Play the alarm sequence
                AlarmPlaybackHelper(context).play(alarmSet, alarmEvent)

                // Reschedule for next occurrence
                repository.scheduleAlarm(alarmSet, alarmEvent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

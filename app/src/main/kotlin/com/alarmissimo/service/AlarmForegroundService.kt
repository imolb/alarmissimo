package com.alarmissimo.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alarmissimo.AlarmissimoApp
import com.alarmissimo.R
import com.alarmissimo.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that drives alarm playback.
 *
 * A [Service] is required because [android.content.BroadcastReceiver.goAsync] is capped at
 * roughly 10 seconds on Android 8+ — too short for TTS initialisation + speech.  Running as a
 * foreground service keeps the process alive and posts the mandatory notification so the user
 * knows an alarm is firing.
 *
 * Started by [com.alarmissimo.receiver.AlarmReceiver] via [startForegroundService].
 */
class AlarmForegroundService : Service() {

    companion object {
        const val EXTRA_ALARM_SET_ID   = "alarm_set_id"
        const val EXTRA_ALARM_EVENT_ID = "alarm_event_id"
        const val EXTRA_ALARM_MESSAGE  = "alarm_message"
        private const val NOTIFICATION_ID = 1001

        /** Convenience builder so callers never hard-code extra names. */
        fun buildIntent(context: Context, alarmSetId: Long, alarmEventId: Long, message: String = ""): Intent =
            Intent(context, AlarmForegroundService::class.java).apply {
                putExtra(EXTRA_ALARM_SET_ID, alarmSetId)
                putExtra(EXTRA_ALARM_EVENT_ID, alarmEventId)
                putExtra(EXTRA_ALARM_MESSAGE, message)
            }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Post the foreground notification immediately so Android allows us to keep running.
        val message = intent?.getStringExtra(EXTRA_ALARM_MESSAGE) ?: ""
        startForeground(NOTIFICATION_ID, buildNotification(message))

        val alarmSetId   = intent?.getLongExtra(EXTRA_ALARM_SET_ID, -1L) ?: -1L
        val alarmEventId = intent?.getLongExtra(EXTRA_ALARM_EVENT_ID, -1L) ?: -1L

        if (alarmSetId == -1L || alarmEventId == -1L) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val repository = (applicationContext as AlarmissimoApp).repository

        serviceScope.launch {
            try {
                val alarmSets  = repository.getAlarmSets()
                val alarmSet   = alarmSets.find { it.id == alarmSetId }   ?: return@launch
                val alarmEvent = alarmSet.alarmEvents.find { it.id == alarmEventId } ?: return@launch

                // Play gong + TTS — this may take 5–30 s; safe here inside a foreground service.
                AlarmPlaybackHelper(applicationContext).play(alarmSet, alarmEvent)

                // Reschedule for next occurrence after successful playback.
                repository.scheduleAlarm(alarmSet, alarmEvent)
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun buildNotification(message: String = ""): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AlarmissimoApp.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.alarmissimo_icon)
            .setContentTitle("Alarmissimo")
            .setContentText(message.ifEmpty { "Alarm wird abgespielt …" })
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)  // suppress default notification sound; alarm plays its own audio
            .build()
    }
}

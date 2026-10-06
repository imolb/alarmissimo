package com.alarmissimo.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alarmissimo.AlarmissimoApp
import com.alarmissimo.R
import com.alarmissimo.data.model.VoiceProfile
import com.alarmissimo.ui.MainActivity
import com.alarmissimo.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that drives alarm playback.
 *
 * - Checks [AlarmSet.enabled] **and** [AlarmEvent.enabled] at fire time.
 * - Saves / restores [AudioManager.STREAM_ALARM] volume around playback.
 * - Date-specific alarms are NOT rescheduled after firing.
 * - Cancels the BT keep-alive alarm after playback completes when it was the last event.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val EXTRA_ALARM_SET_ID   = "alarm_set_id"
        const val EXTRA_ALARM_EVENT_ID = "alarm_event_id"
        const val EXTRA_ALARM_MESSAGE  = "alarm_message"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BtAutoDisconn"

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

                // Check enabled at fire time (not only at schedule time)
                if (!alarmSet.enabled || !alarmEvent.enabled) return@launch

                // Route to BT A2DP when connected (STREAM_MUSIC), otherwise STREAM_ALARM
                val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val useBt = audioManager.isBluetoothA2dpOn()
                val audioStream = if (useBt) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
                val savedVolume = audioManager.getStreamVolume(audioStream)
                val maxVolume   = audioManager.getStreamMaxVolume(audioStream)

                try {
                    audioManager.setStreamVolume(audioStream,
                        (maxVolume * alarmSet.audioVolume / 100.0).toInt().coerceIn(0, maxVolume), 0)
                    // Resolve voice profile
                    val voiceProfile = if (alarmEvent.voiceProfileId == 0L) {
                        VoiceProfile.STANDARD_PROFILE
                    } else {
                        repository.getVoiceProfiles()
                            .find { it.id == alarmEvent.voiceProfileId }
                            ?: VoiceProfile.STANDARD_PROFILE
                    }

                    AlarmPlaybackHelper(applicationContext).play(alarmEvent, voiceProfile, alarmSet.endEventName, useBluetooth = useBt)
                } finally {
                    audioManager.setStreamVolume(audioStream, savedVolume, 0)
                }

                // Date-specific alarms are not rescheduled after firing
                if (alarmSet.specificDate == null) {
                    val btAheadMinutes = repository.getSoundDeviceConfig().btWarningAheadMinutes
                    repository.scheduleAlarm(alarmSet, alarmEvent, btAheadMinutes)
                }

                // BT auto-disconnect: if gap to next alarm exceeds the lookahead window, disconnect
                val soundConfig = repository.getSoundDeviceConfig()
                if (soundConfig.btAutoDisconnectEnabled) {
                    val allSets = repository.getAlarmSets()
                    val now = System.currentTimeMillis()
                    val windowMs = soundConfig.btAutoDisconnectAheadMinutes * 60_000L
                    val nextAlarmMs = allSets
                        .filter { it.enabled }
                        .flatMap { s -> s.alarmEvents.filter { e -> e.enabled }.mapNotNull { e -> TimeUtils.computeTriggerMillis(s, e) } }
                        .filter { it > now }
                        .minOrNull()
                    val gapMin = nextAlarmMs?.let { (it - now) / 60_000 }
                    val shouldDisconnect = nextAlarmMs == null || (nextAlarmMs - now) > windowMs
                    Log.d(TAG, "post-playback check: nextAlarm in ${gapMin ?: "∞"} min, window=${soundConfig.btAutoDisconnectAheadMinutes} min → ${if (shouldDisconnect) "DISCONNECTING BT" else "keeping BT connected"}")
                    if (shouldDisconnect) {
                        // Wait until audio system is truly idle (covers BT speaker buffer drain)
                        val deadline = System.currentTimeMillis() + 3_000L
                        while (audioManager.isMusicActive() && System.currentTimeMillis() < deadline) {
                            delay(50)
                        }
                        disconnectBtA2dp()
                    }
                }
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    @Suppress("MissingPermission")
    private fun disconnectBtA2dp() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = btManager.adapter ?: return
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val a2dp = proxy as BluetoothA2dp
                try {
                    for (device in a2dp.connectedDevices) {
                        disconnectBtDevice(a2dp, device)
                    }
                } finally {
                    adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                }
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    private fun disconnectBtDevice(a2dp: BluetoothA2dp, device: BluetoothDevice) {
        try {
            val method = BluetoothA2dp::class.java.getMethod("disconnect", BluetoothDevice::class.java)
            method.invoke(a2dp, device)
            Log.d(TAG, "disconnected BT device: ${device.address}")
        } catch (e: Exception) {
            Log.w(TAG, "disconnect failed for ${device.address}: $e")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Alarmissimo")
            .setContentText(message.ifEmpty { "Alarm wird abgespielt …" })
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)
            .build()
    }
}

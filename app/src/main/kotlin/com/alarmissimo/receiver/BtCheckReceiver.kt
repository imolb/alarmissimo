package com.alarmissimo.receiver

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alarmissimo.AlarmissimoApp
import com.alarmissimo.R
import com.alarmissimo.ui.MainActivity
import android.app.PendingIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Broadcast receiver that fires before an alarm to check whether the correct
 * Bluetooth audio device is connected. If none of the accepted devices is
 * connected it posts a warning notification.
 *
 * Scheduled by [com.alarmissimo.data.AlarmRepository.scheduleBtCheck].
 */
class BtCheckReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_EVENT_ID = "alarm_event_id"
        const val EXTRA_ALARM_SET_ID   = "alarm_set_id"

        /** Deterministic request code derived from the alarm-event ID. */
        fun requestCode(eventId: Long): Int = (0x7FFF0000 or (eventId and 0xFFFF).toInt())

        private const val TAG = "BtCheckReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext as AlarmissimoApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val async = goAsync()

        scope.launch {
            try {
                val config = appContext.repository.getSoundDeviceConfig()
                val eventId = intent.getLongExtra(EXTRA_ALARM_EVENT_ID, -1L)
                val setId   = intent.getLongExtra(EXTRA_ALARM_SET_ID, -1L)
                Log.d(TAG, "--- BT check fired for eventId=$eventId setId=$setId ---")

                if (!config.btWarningEnabled || config.acceptedDevices.isEmpty()) {
                    Log.d(TAG, "warning disabled or no accepted devices — skipping")
                    return@launch
                }

                // Check if any accepted device is currently connected
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = btManager.adapter
                @Suppress("MissingPermission")
                val connectedMacs: Set<String> = try {
                    adapter?.bondedDevices?.filter { it.isConnected() }?.map { it.address }?.toSet() ?: emptySet()
                } catch (e: SecurityException) {
                    emptySet()
                }

                Log.d(TAG, "accepted=${config.acceptedDevices.map { it.mac }}, connected=$connectedMacs")
                val anyAcceptedConnected = config.acceptedDevices.any { it.mac in connectedMacs }
                Log.d(TAG, "anyAcceptedConnected=$anyAcceptedConnected → ${if (anyAcceptedConnected) "no warning" else "POSTING WARNING NOTIFICATION"}")

                if (!anyAcceptedConnected) {
                    postWarningNotification(context, eventId)
                }
            } finally {
                async.finish()
            }
        }
    }

    private fun postWarningNotification(context: Context, eventId: Long) {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, AlarmissimoApp.BT_WARNING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Alarmissimo")
            .setContentText("Bitte geeignetes Bluetooth-Gerät verbinden.")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(eventId.toInt(), notif)
    }

    // BluetoothDevice.isConnected() is available via reflection or the profile manager;
    // this simple helper uses the connection state check.
    @Suppress("MissingPermission")
    private fun android.bluetooth.BluetoothDevice.isConnected(): Boolean {
        return try {
            val method = this.javaClass.getMethod("isConnected")
            method.invoke(this) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }
}

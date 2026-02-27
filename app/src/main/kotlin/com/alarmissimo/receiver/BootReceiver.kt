package com.alarmissimo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmissimo.AlarmissimoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules all enabled alarms after the device boots.
 *
 * Registered for [Intent.ACTION_BOOT_COMPLETED] in AndroidManifest.xml.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as AlarmissimoApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmSets = app.repository.getAlarmSets()
                app.repository.scheduleAllAlarms(alarmSets)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

package com.alarmissimo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Application class — initialises global singletons and notification channels.
 */
class AlarmissimoApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: AlarmRepository
        private set

    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)
        repository = AlarmRepository(this, dataStoreManager)
        createNotificationChannels()

        appScope.launch {
            dataStoreManager.initializeIfEmpty()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Alarmissimo Alarme",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Benachrichtigungen bei Alarmauslösung"
                    setBypassDnd(true)
                    setSound(null, null)
                }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    BT_WARNING_CHANNEL_ID,
                    "Bluetooth-Warnung",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Warnung wenn kein geeignetes Bluetooth-Gerät verbunden ist"
                }
            )
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID      = "alarm_channel"
        const val BT_WARNING_CHANNEL_ID = "bt_warning_channel"
    }
}

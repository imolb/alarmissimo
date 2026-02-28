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
 * Application class — initialises global singletons and the notification channel.
 */
class AlarmissimoApp : Application() {

    /** Application-scoped coroutine scope. Cancelled when the process is killed. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: AlarmRepository
        private set

    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)
        repository = AlarmRepository(this, dataStoreManager)
        createNotificationChannel()

        // Populate default demo configuration on first launch (async; UI handles empty state)
        appScope.launch {
            dataStoreManager.initializeIfEmpty()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarmissimo Alarme",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Benachrichtigungen bei Alarmauslösung"
                setBypassDnd(true)
                setSound(null, null)  // suppress default notification sound; alarm plays its own audio
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }
}

package com.alarmissimo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.DataStoreManager

/**
 * Application class — initialises global singletons and the notification channel.
 */
class AlarmissimoApp : Application() {

    lateinit var repository: AlarmRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val dataStoreManager = DataStoreManager(this)
        repository = AlarmRepository(this, dataStoreManager)
        createNotificationChannel()
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
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }
}

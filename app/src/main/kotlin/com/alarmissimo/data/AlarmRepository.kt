package com.alarmissimo.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.receiver.AlarmReceiver
import com.alarmissimo.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Central repository for alarm-set data and AlarmManager scheduling.
 *
 * Uses [DataStoreManager] for persistence and [AlarmManager] for exact alarm scheduling.
 *
 * @param context Application context.
 * @param dataStoreManager The DataStore persistence layer.
 */
class AlarmRepository(
    private val context: Context,
    private val dataStoreManager: DataStoreManager
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Emits the current list of alarm-sets. */
    val alarmSetsFlow: Flow<List<AlarmSet>> = dataStoreManager.alarmSetsFlow

    /**
     * Saves the given list of alarm-sets and reschedules all enabled alarms.
     *
     * @param alarmSets The updated list to persist and schedule.
     */
    suspend fun saveAndSchedule(alarmSets: List<AlarmSet>) {
        dataStoreManager.saveAlarmSets(alarmSets)
        cancelAllAlarms(alarmSets)
        scheduleAllAlarms(alarmSets)
    }

    /**
     * Returns the current list of alarm-sets (one-shot read).
     */
    suspend fun getAlarmSets(): List<AlarmSet> = dataStoreManager.alarmSetsFlow.first()

    // ── Scheduling ──────────────────────────────────────────────────────────

    /**
     * Schedules the next occurrence of all enabled alarm-events across all alarm-sets.
     *
     * @param alarmSets Alarm-sets to schedule.
     */
    fun scheduleAllAlarms(alarmSets: List<AlarmSet>) {
        alarmSets.filter { it.enabled }.forEach { set ->
            set.alarmEvents.forEach { event ->
                scheduleAlarm(set, event)
            }
        }
    }

    /**
     * Cancels all pending alarms for every alarm-event in the provided list.
     *
     * @param alarmSets Alarm-sets whose alarms should be cancelled.
     */
    fun cancelAllAlarms(alarmSets: List<AlarmSet>) {
        alarmSets.forEach { set ->
            set.alarmEvents.forEach { event ->
                cancelAlarm(event)
            }
        }
    }

    /**
     * Schedules the next occurrence of a single alarm-event.
     *
     * @param set The parent alarm-set (provides weekdays and volume).
     * @param event The alarm-event to schedule.
     */
    fun scheduleAlarm(set: AlarmSet, event: AlarmEvent) {
        val triggerAt = TimeUtils.nextOccurrenceMillis(event.time, set.weekdays) ?: return
        val intent = alarmIntent(set, event)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
    }

    /**
     * Cancels a pending alarm for a single alarm-event.
     *
     * @param event The alarm-event whose pending alarm should be cancelled.
     */
    fun cancelAlarm(event: AlarmEvent) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                event.id.toInt(),
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
        )
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun alarmIntent(set: AlarmSet, event: AlarmEvent): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_EVENT_ID, event.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_SET_ID, set.id)
        }
        return PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

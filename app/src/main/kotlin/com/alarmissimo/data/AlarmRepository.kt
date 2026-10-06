package com.alarmissimo.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.data.model.SoundDeviceConfig
import com.alarmissimo.data.model.VoiceProfile
import com.alarmissimo.receiver.AlarmReceiver
import com.alarmissimo.receiver.BtCheckReceiver
import com.alarmissimo.receiver.BtKeepAliveReceiver
import com.alarmissimo.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Central repository for alarm-set data, voice profiles, sound device config,
 * and AlarmManager scheduling.
 *
 * @param context Application context.
 * @param dataStoreManager The DataStore persistence layer.
 */
class AlarmRepository(
    private val context: Context,
    private val dataStoreManager: DataStoreManager
) {
    private companion object {
        private const val TAG = "AlarmRepository"
    }
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ── Flows ────────────────────────────────────────────────────────────────

    val alarmSetsFlow: Flow<List<AlarmSet>> = dataStoreManager.alarmSetsFlow

    /** User-created profiles only. Standard profile (id=0) is a constant — see [VoiceProfile.STANDARD_PROFILE]. */
    val voiceProfilesFlow: Flow<List<VoiceProfile>> = dataStoreManager.voiceProfilesFlow

    val soundDeviceConfigFlow: Flow<SoundDeviceConfig> = dataStoreManager.soundDeviceConfigFlow

    // ── Alarm-set CRUD ───────────────────────────────────────────────────────

    /** Saves the list and reschedules all enabled alarms. */
    suspend fun saveAndSchedule(alarmSets: List<AlarmSet>) {
        dataStoreManager.saveAlarmSets(alarmSets)
        cancelAllAlarms(alarmSets)
        val btAheadMinutes = getSoundDeviceConfig().btWarningAheadMinutes
        scheduleAllAlarms(alarmSets, btAheadMinutes)
    }

    suspend fun getAlarmSets(): List<AlarmSet> = dataStoreManager.alarmSetsFlow.first()

    /**
     * Duplicates an alarm-set appending " (Kopie)" (with counter suffix for collisions).
     * @return The new set's ID, or null if [id] was not found.
     */
    suspend fun duplicateAlarmSet(id: Long): Long? {
        val all = getAlarmSets().toMutableList()
        val original = all.find { it.id == id } ?: return null
        val newId = System.currentTimeMillis()
        val baseName = "${original.name} (Kopie)"
        val existingNames = all.map { it.name }.toSet()
        val finalName = if (baseName !in existingNames) {
            baseName
        } else {
            var counter = 2
            while ("$baseName $counter" in existingNames) counter++
            "$baseName $counter"
        }
        val copy = original.copy(
            id = newId,
            name = finalName,
            alarmEvents = original.alarmEvents.map { it.copy(id = System.currentTimeMillis() + it.id % 1000) }
        )
        all.add(copy)
        saveAndSchedule(all)
        return newId
    }

    /** Toggles enabled on an alarm set and persists immediately. */
    suspend fun setAlarmSetEnabled(setId: Long, enabled: Boolean) {
        val all = getAlarmSets().toMutableList()
        val idx = all.indexOfFirst { it.id == setId }
        if (idx < 0) return
        all[idx] = all[idx].copy(enabled = enabled)
        saveAndSchedule(all)
    }

    /** Toggles enabled on an alarm event and persists immediately. */
    suspend fun setAlarmEventEnabled(setId: Long, eventId: Long, enabled: Boolean) {
        val all = getAlarmSets().toMutableList()
        val setIdx = all.indexOfFirst { it.id == setId }
        if (setIdx < 0) return
        val events = all[setIdx].alarmEvents.toMutableList()
        val eIdx = events.indexOfFirst { it.id == eventId }
        if (eIdx < 0) return
        events[eIdx] = events[eIdx].copy(enabled = enabled)
        all[setIdx] = all[setIdx].copy(alarmEvents = events)
        saveAndSchedule(all)
    }

    // ── Voice Profile CRUD ───────────────────────────────────────────────────

    suspend fun getVoiceProfiles(): List<VoiceProfile> = dataStoreManager.voiceProfilesFlow.first()

    /** Returns all profiles including the built-in Standard profile (id=0) at index 0. */
    suspend fun getAllVoiceProfiles(): List<VoiceProfile> =
        listOf(VoiceProfile.STANDARD_PROFILE) + getVoiceProfiles()

    suspend fun saveVoiceProfiles(profiles: List<VoiceProfile>) =
        dataStoreManager.saveVoiceProfiles(profiles)

    suspend fun addVoiceProfile(profile: VoiceProfile) {
        val current = getVoiceProfiles().toMutableList()
        current.add(profile)
        saveVoiceProfiles(current)
    }

    suspend fun updateVoiceProfile(profile: VoiceProfile) {
        val current = getVoiceProfiles().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        saveVoiceProfiles(current)
    }

    suspend fun deleteVoiceProfile(profileId: Long) {
        val current = getVoiceProfiles().filter { it.id != profileId }
        saveVoiceProfiles(current)
        // Reset any alarm events referencing the deleted profile back to Standard
        val sets = getAlarmSets().map { set ->
            set.copy(alarmEvents = set.alarmEvents.map { event ->
                if (event.voiceProfileId == profileId) event.copy(voiceProfileId = 0L) else event
            })
        }
        dataStoreManager.saveAlarmSets(sets)
    }

    suspend fun duplicateVoiceProfile(profileId: Long): Long? {
        val all = if (profileId == 0L) {
            listOf(VoiceProfile.STANDARD_PROFILE) + getVoiceProfiles()
        } else {
            getVoiceProfiles()
        }
        val original = all.find { it.id == profileId } ?: return null
        val newId = System.currentTimeMillis()
        val baseName = "${original.name} (Kopie)"
        val existingNames = all.map { it.name }.toSet()
        val finalName = if (baseName !in existingNames) {
            baseName
        } else {
            var counter = 2
            while ("$baseName $counter" in existingNames) counter++
            "$baseName $counter"
        }
        val copy = original.copy(id = newId, name = finalName)
        addVoiceProfile(copy)
        return newId
    }

    // ── Sound Device Config ──────────────────────────────────────────────────

    suspend fun getSoundDeviceConfig(): SoundDeviceConfig =
        dataStoreManager.soundDeviceConfigFlow.first()

    suspend fun saveSoundDeviceConfig(config: SoundDeviceConfig) {
        dataStoreManager.saveSoundDeviceConfig(config)
        rescheduleKeepAlive(config)
        // Reschedule all BtCheck alarms with the (potentially new) btWarningAheadMinutes
        scheduleAllAlarms(getAlarmSets(), config.btWarningAheadMinutes)
    }

    // ── Scheduling ───────────────────────────────────────────────────────────

    fun scheduleAllAlarms(alarmSets: List<AlarmSet>, btAheadMinutes: Int = 10) {
        alarmSets.filter { it.enabled }.forEach { set ->
            set.alarmEvents.filter { it.enabled }.forEach { event ->
                scheduleAlarm(set, event, btAheadMinutes)
            }
        }
    }

    fun cancelAllAlarms(alarmSets: List<AlarmSet>) {
        alarmSets.forEach { set ->
            set.alarmEvents.forEach { event ->
                cancelAlarm(event)
                cancelBtCheck(event)
            }
        }
    }

    fun scheduleAlarm(set: AlarmSet, event: AlarmEvent, btAheadMinutes: Int = 10) {
        if (!set.enabled || !event.enabled) return
        val triggerAt = TimeUtils.computeTriggerMillis(set, event) ?: return
        val intent = alarmIntent(set, event)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerAt))
        Log.d(TAG, "scheduleAlarm: set='${set.name}' event='${event.time}' → $fmt (eventId=${event.id})")

        // Schedule BT warning check before the alarm
        scheduleBtCheck(set, event, triggerAt, btAheadMinutes)
    }

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

    /** Schedules keeps-alive pings when [config.btKeepAliveEnabled] is true. */
    fun rescheduleKeepAlive(config: SoundDeviceConfig) {
        cancelKeepAlive()
        if (!config.btKeepAliveEnabled) return
        val intervalMs = config.btKeepAliveIntervalMinutes * 60_000L
        val triggerAt = System.currentTimeMillis() + intervalMs
        val intent = PendingIntent.getBroadcast(
            context,
            BtKeepAliveReceiver.REQUEST_CODE,
            Intent(context, BtKeepAliveReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
    }

    fun cancelKeepAlive() {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                BtKeepAliveReceiver.REQUEST_CODE,
                Intent(context, BtKeepAliveReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun scheduleBtCheck(set: AlarmSet, event: AlarmEvent, alarmTriggerMs: Long, btAheadMinutes: Int) {
        val aheadMs = btAheadMinutes * 60_000L
        val checkAt = alarmTriggerMs - aheadMs
        if (checkAt <= System.currentTimeMillis()) {
            Log.d(TAG, "scheduleBtCheck: skipped (checkAt already past) for eventId=${event.id}")
            return
        }
        val intent = PendingIntent.getBroadcast(
            context,
            BtCheckReceiver.requestCode(event.id),
            Intent(context, BtCheckReceiver::class.java).apply {
                putExtra(BtCheckReceiver.EXTRA_ALARM_EVENT_ID, event.id)
                putExtra(BtCheckReceiver.EXTRA_ALARM_SET_ID, set.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkAt, intent)
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(checkAt))
        Log.d(TAG, "scheduleBtCheck: set='${set.name}' event='${event.time}' btCheck→$fmt ($btAheadMinutes min before alarm, eventId=${event.id})")
    }

    private fun cancelBtCheck(event: AlarmEvent) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                BtCheckReceiver.requestCode(event.id),
                Intent(context, BtCheckReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
        )
    }

    private fun alarmIntent(set: AlarmSet, event: AlarmEvent): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_EVENT_ID, event.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_SET_ID, set.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_MESSAGE, event.message)
        }
        return PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

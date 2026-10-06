package com.alarmissimo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.data.model.SoundDeviceConfig
import com.alarmissimo.data.model.VoiceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "alarmissimo_prefs")

/**
 * Manages persistence via Jetpack DataStore (JSON via kotlinx.serialization).
 *
 * Keys:
 *  - [KEY_ALARM_SETS]       — JSON array of [AlarmSet]
 *  - [KEY_VOICE_PROFILES]   — JSON array of user-created [VoiceProfile] (Standard id=0 not stored)
 *  - [KEY_SOUND_DEVICE]     — JSON [SoundDeviceConfig]
 *
 * @param context Application context.
 */
class DataStoreManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_ALARM_SETS     = stringPreferencesKey("alarm_sets")
        private val KEY_VOICE_PROFILES = stringPreferencesKey("voice_profiles")
        private val KEY_SOUND_DEVICE   = stringPreferencesKey("sound_device_config")
    }

    /** Emits the current list of alarm-sets whenever the stored value changes. */
    val alarmSetsFlow: Flow<List<AlarmSet>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_ALARM_SETS] ?: return@map emptyList()
        try {
            json.decodeFromString<List<AlarmSet>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Emits user-created [VoiceProfile]s (id > 0). Standard profile (id=0) not included. */
    val voiceProfilesFlow: Flow<List<VoiceProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_VOICE_PROFILES] ?: return@map emptyList()
        try {
            json.decodeFromString<List<VoiceProfile>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Emits the current [SoundDeviceConfig] whenever it changes. */
    val soundDeviceConfigFlow: Flow<SoundDeviceConfig> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_SOUND_DEVICE] ?: return@map SoundDeviceConfig()
        try {
            json.decodeFromString<SoundDeviceConfig>(raw)
        } catch (e: Exception) {
            SoundDeviceConfig()
        }
    }

    /** Persists the full list of alarm-sets. */
    suspend fun saveAlarmSets(alarmSets: List<AlarmSet>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALARM_SETS] = json.encodeToString(alarmSets)
        }
    }

    /** Persists user-created voice profiles. Standard profile (id=0) is filtered out. */
    suspend fun saveVoiceProfiles(profiles: List<VoiceProfile>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOICE_PROFILES] = json.encodeToString(profiles.filter { it.id != 0L })
        }
    }

    /** Persists the global [SoundDeviceConfig]. */
    suspend fun saveSoundDeviceConfig(config: SoundDeviceConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUND_DEVICE] = json.encodeToString(config)
        }
    }

    /**
     * Populates a default demo set on first startup.
     * Called once from Application.onCreate().
     */
    suspend fun initializeIfEmpty() {
        val existing = alarmSetsFlow.first()
        if (existing.isNotEmpty()) return

        val demoEvent = AlarmEvent(
            id = System.currentTimeMillis(),
            time = "07:30",
            gong = "gong1x",
            timePlayback = true,
            message = "John, es ist Zeit, die Schuhe anzuziehen."
        )
        val demoSet = AlarmSet(
            id = System.currentTimeMillis() + 1,
            name = "Demo",
            enabled = true,
            weekdays = (1..5).toList(),
            alarmEvents = listOf(demoEvent)
        )
        saveAlarmSets(listOf(demoSet))
    }
}

package com.alarmissimo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.data.model.VoiceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "alarmissimo_prefs")

/**
 * Manages persistence of alarm-sets via Jetpack DataStore (JSON via kotlinx.serialization).
 *
 * All alarm-sets are serialized as a single JSON array and stored under [KEY_ALARM_SETS].
 *
 * @param context Application context.
 */
class DataStoreManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_ALARM_SETS = stringPreferencesKey("alarm_sets")
        private val KEY_VOICE_CONFIG = stringPreferencesKey("voice_config")
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

    /** Emits the current [VoiceConfig] whenever it changes. */
    val voiceConfigFlow: Flow<VoiceConfig> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_VOICE_CONFIG] ?: return@map VoiceConfig()
        try {
            json.decodeFromString<VoiceConfig>(raw)
        } catch (e: Exception) {
            VoiceConfig()
        }
    }

    /**
     * Persists the full list of alarm-sets.
     *
     * @param alarmSets The complete, current list to store.
     */
    suspend fun saveAlarmSets(alarmSets: List<AlarmSet>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALARM_SETS] = json.encodeToString(alarmSets)
        }
    }

    /** Persists the global [VoiceConfig]. */
    suspend fun saveVoiceConfig(config: VoiceConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VOICE_CONFIG] = json.encodeToString(config)
        }
    }

    /**
     * Creates a default demo configuration on first startup (empty DataStore).
     * Called once from Application.onCreate().
     */
    suspend fun initializeIfEmpty() {
        val existing = alarmSetsFlow.first()
        if (existing.isNotEmpty()) return   // Already has data — nothing to do

        val demoEvent = AlarmEvent(
            id = System.currentTimeMillis(),
            time = "07:30",
            gong = "gong",
            timePlayback = true,
            message = "John, es ist Zeit, die Schuhe anzuziehen."
        )
        val demoSet = AlarmSet(
            id = System.currentTimeMillis() + 1,
            name = "Demo",
            enabled = true,
            weekdays = (1..5).toList(),   // Monday–Friday
            audioVolume = 80,
            alarmEvents = listOf(demoEvent)
        )
        saveAlarmSets(listOf(demoSet))
    }
}

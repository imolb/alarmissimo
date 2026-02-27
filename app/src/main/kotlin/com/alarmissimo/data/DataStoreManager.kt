package com.alarmissimo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alarmissimo.data.model.AlarmSet
import kotlinx.coroutines.flow.Flow
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
}

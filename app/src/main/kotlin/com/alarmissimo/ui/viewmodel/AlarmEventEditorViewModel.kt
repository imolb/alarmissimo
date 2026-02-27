package com.alarmissimo.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.service.AlarmPlaybackHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the AlarmEvent editor.
 *
 * @param alarmEvent The alarm-event being edited, or null while loading.
 * @param isSaved Whether the last save operation completed.
 */
data class AlarmEventEditorUiState(
    val alarmEvent: AlarmEvent? = null,
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Alarm-Event Editor screen.
 *
 * @param alarmSetId The parent alarm-set ID.
 * @param alarmEventId The ID of the alarm-event to edit (-1 = new).
 * @param repository The alarm data repository.
 */
class AlarmEventEditorViewModel(
    private val alarmSetId: Long,
    private val alarmEventId: Long,
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmEventEditorUiState())
    val uiState: StateFlow<AlarmEventEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val sets = repository.getAlarmSets()
            val alarmSet = sets.find { it.id == alarmSetId }
            val event = alarmSet?.alarmEvents?.find { it.id == alarmEventId }
                ?: AlarmEvent(
                    id = System.currentTimeMillis(),
                    time = "07:00",
                    gong = "none",
                    timePlayback = true,
                    message = ""
                )
            _uiState.value = AlarmEventEditorUiState(alarmEvent = event)
        }
    }

    /** Updates the in-memory alarm-event with [updated]. */
    fun update(updated: AlarmEvent) {
        _uiState.value = _uiState.value.copy(alarmEvent = updated)
    }

    /** Persists the current alarm-event state. */
    fun save() {
        val event = _uiState.value.alarmEvent ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val setIdx = all.indexOfFirst { it.id == alarmSetId }
            if (setIdx < 0) return@launch
            val set = all[setIdx]
            val events = set.alarmEvents.toMutableList()
            val eIdx = events.indexOfFirst { it.id == event.id }
            if (eIdx >= 0) events[eIdx] = event else events.add(event)
            all[setIdx] = set.copy(alarmEvents = events.sortedBy { it.time })
            repository.saveAndSchedule(all)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    /** Deletes the current alarm-event. */
    fun delete() {
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val setIdx = all.indexOfFirst { it.id == alarmSetId }
            if (setIdx < 0) return@launch
            val set = all[setIdx]
            all[setIdx] = set.copy(alarmEvents = set.alarmEvents.filter { it.id != alarmEventId })
            repository.saveAndSchedule(all)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    /** Duplicates the current alarm-event with a new ID. */
    fun duplicate() {
        val event = _uiState.value.alarmEvent ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val setIdx = all.indexOfFirst { it.id == alarmSetId }
            if (setIdx < 0) return@launch
            val set = all[setIdx]
            val copy = event.copy(id = System.currentTimeMillis())
            all[setIdx] = set.copy(alarmEvents = (set.alarmEvents + copy).sortedBy { it.time })
            repository.saveAndSchedule(all)
        }
    }

    /**
     * Plays the alarm immediately for preview purposes.
     *
     * @param context Application context for [AlarmPlaybackHelper].
     */
    fun playNow(context: Context) {
        val event = _uiState.value.alarmEvent ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val sets = repository.getAlarmSets()
            val set = sets.find { it.id == alarmSetId } ?: return@launch
            AlarmPlaybackHelper(context).play(set, event)
        }
    }
}

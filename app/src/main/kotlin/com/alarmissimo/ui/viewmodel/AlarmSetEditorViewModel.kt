package com.alarmissimo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the AlarmSet editor.
 *
 * @param alarmSet The alarm-set being edited, or null while loading.
 * @param isSaved Whether the last save operation completed successfully.
 */
data class AlarmSetEditorUiState(
    val alarmSet: AlarmSet? = null,
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Alarm-Set Editor screen.
 * Loads the alarm-set by ID, exposes editable state, and persists changes.
 *
 * @param alarmSetId The ID of the alarm-set to edit (-1 = new).
 * @param repository The alarm data repository.
 */
class AlarmSetEditorViewModel(
    private val alarmSetId: Long,
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmSetEditorUiState())
    val uiState: StateFlow<AlarmSetEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val sets = repository.getAlarmSets()
            val set = sets.find { it.id == alarmSetId } ?: AlarmSet(
                id = System.currentTimeMillis(),
                name = "",
                enabled = true,
                weekdays = (1..7).toList(),
                audioVolume = 80,
                alarmEvents = emptyList()
            )
            _uiState.value = AlarmSetEditorUiState(alarmSet = set)
        }
    }

    /** Updates the in-memory alarm-set with [updated]. */
    fun update(updated: AlarmSet) {
        _uiState.value = _uiState.value.copy(alarmSet = updated)
    }

    /** Persists the current alarm-set state. */
    fun save() {
        val set = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val idx = all.indexOfFirst { it.id == set.id }
            if (idx >= 0) all[idx] = set else all.add(set)
            repository.saveAndSchedule(all)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    /** Deletes the current alarm-set. */
    fun delete() {
        viewModelScope.launch {
            val updated = repository.getAlarmSets().filter { it.id != alarmSetId }
            repository.saveAndSchedule(updated)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    /** Duplicates the current alarm-set with new IDs. */
    fun duplicate() {
        val set = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val copy = set.copy(
                id = System.currentTimeMillis(),
                alarmEvents = set.alarmEvents.map { it.copy(id = System.currentTimeMillis() + it.id % 1000) }
            )
            all.add(copy)
            repository.saveAndSchedule(all)
        }
    }

    /** Adds a new alarm-event to the current alarm-set. */
    fun addAlarmEvent() {
        val set = _uiState.value.alarmSet ?: return
        val newEvent = AlarmEvent(
            id = System.currentTimeMillis(),
            time = "07:00",
            gong = "none",
            timePlayback = true,
            message = ""
        )
        update(set.copy(alarmEvents = set.alarmEvents + newEvent))
        save()
    }
}

package com.alarmissimo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlarmSetEditorUiState(
    val alarmSet: AlarmSet? = null,
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Alarm-Set Editor screen.
 *
 * Back-navigation auto-saves (no explicit Save button).
 * Subscribes to DataStore so alarm-event changes from [AlarmEventEditorViewModel]
 * are reflected when the user navigates back.
 */
class AlarmSetEditorViewModel(
    private val alarmSetId: Long,
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmSetEditorUiState())
    val uiState: StateFlow<AlarmSetEditorUiState> = _uiState.asStateFlow()

    private val _navigateToAlarmEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToAlarmEvent: SharedFlow<Long> = _navigateToAlarmEvent.asSharedFlow()

    private val _navigateToAlarmSet = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToAlarmSet: SharedFlow<Long> = _navigateToAlarmSet.asSharedFlow()

    private var hasLocalEdits = false

    init {
        viewModelScope.launch {
            repository.alarmSetsFlow.collect { sets ->
                val refreshed = sets.find { it.id == alarmSetId }
                val current = _uiState.value
                when {
                    current.alarmSet == null -> {
                        _uiState.value = current.copy(
                            alarmSet = refreshed ?: AlarmSet(
                                id = alarmSetId,
                                name = "",
                                enabled = true,
                                weekdays = (1..7).toList(),
                                alarmEvents = emptyList()
                            )
                        )
                    }
                    refreshed != null && hasLocalEdits -> {
                        val merged = current.alarmSet!!.copy(alarmEvents = refreshed.alarmEvents)
                        _uiState.value = current.copy(alarmSet = merged)
                    }
                    refreshed != null -> {
                        _uiState.value = current.copy(alarmSet = refreshed)
                    }
                }
            }
        }
    }

    fun update(updated: AlarmSet) {
        hasLocalEdits = true
        _uiState.value = _uiState.value.copy(alarmSet = updated)
    }

    /** Called on back navigation — persists current state. */
    fun save() {
        val localSet = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val idx = all.indexOfFirst { it.id == localSet.id }
            if (idx >= 0) {
                val storedEvents = all[idx].alarmEvents
                all[idx] = localSet.copy(alarmEvents = storedEvents)
            } else {
                all.add(localSet)
            }
            repository.saveAndSchedule(all)
            hasLocalEdits = false
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun delete() {
        viewModelScope.launch {
            val updated = repository.getAlarmSets().filter { it.id != alarmSetId }
            repository.saveAndSchedule(updated)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun duplicate() {
        val set = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            val newId = repository.duplicateAlarmSet(set.id) ?: return@launch
            _navigateToAlarmSet.tryEmit(newId)
        }
    }

    fun addAlarmEvent() {
        val localSet = _uiState.value.alarmSet ?: return
        val newId = System.currentTimeMillis()
        val newEvent = AlarmEvent(
            id = newId,
            time = "07:00",
            gong = "none",
            timePlayback = true,
            message = ""
        )
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val idx = all.indexOfFirst { it.id == localSet.id }
            val updatedEvents = ((if (idx >= 0) all[idx].alarmEvents else emptyList()) + newEvent)
                .sortedBy { it.time }
            if (idx >= 0) {
                all[idx] = localSet.copy(alarmEvents = updatedEvents)
            } else {
                all.add(localSet.copy(alarmEvents = updatedEvents))
            }
            repository.saveAndSchedule(all)
            hasLocalEdits = false
            _uiState.value = _uiState.value.copy(
                alarmSet = _uiState.value.alarmSet?.copy(alarmEvents = updatedEvents)
            )
            _navigateToAlarmEvent.tryEmit(newId)
        }
    }

    fun duplicateAlarmEvent(eventId: Long) {
        val localSet = _uiState.value.alarmSet ?: return
        val original = localSet.alarmEvents.find { it.id == eventId } ?: return
        val newId = System.currentTimeMillis()
        val copy = original.copy(id = newId)
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val idx = all.indexOfFirst { it.id == localSet.id }
            val storedEvents = if (idx >= 0) all[idx].alarmEvents else emptyList()
            val updatedEvents = (storedEvents + copy).sortedBy { it.time }
            if (idx >= 0) {
                all[idx] = localSet.copy(alarmEvents = updatedEvents)
            } else {
                all.add(localSet.copy(alarmEvents = updatedEvents))
            }
            repository.saveAndSchedule(all)
            hasLocalEdits = false
            _uiState.value = _uiState.value.copy(
                alarmSet = _uiState.value.alarmSet?.copy(alarmEvents = updatedEvents)
            )
            _navigateToAlarmEvent.tryEmit(newId)
        }
    }

    fun deleteAlarmEvent(eventId: Long) {
        val set = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            persistAlarmEvents(set.id, set.alarmEvents.filter { it.id != eventId })
        }
    }

    /**
     * Immediately persists the alarm-set (including all current property edits)
     * with the specified alarm-event's enabled state updated.
     * This avoids the "toggle lost on back" race where save() reads stored events.
     */
    fun toggleAlarmEventEnabled(eventId: Long, enabled: Boolean) {
        val localSet = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val idx = all.indexOfFirst { it.id == localSet.id }
            if (idx >= 0) {
                val updatedEvents = all[idx].alarmEvents.map { e ->
                    if (e.id == eventId) e.copy(enabled = enabled) else e
                }
                all[idx] = localSet.copy(alarmEvents = updatedEvents)
            } else {
                all.add(localSet)
            }
            repository.saveAndSchedule(all)
            hasLocalEdits = false
            _uiState.value = _uiState.value.copy(
                alarmSet = _uiState.value.alarmSet?.let { set ->
                    set.copy(alarmEvents = set.alarmEvents.map { e ->
                        if (e.id == eventId) e.copy(enabled = enabled) else e
                    })
                }
            )
        }
    }

    /**
     * Saves current alarm-set properties to DataStore (preserving stored events),
     * then navigates to the alarm-event editor.
     */
    fun saveAndNavigateToAlarmEvent(eventId: Long) {
        val localSet = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val idx = all.indexOfFirst { it.id == localSet.id }
            if (idx >= 0) {
                val storedEvents = all[idx].alarmEvents
                all[idx] = localSet.copy(alarmEvents = storedEvents)
            } else {
                all.add(localSet)
            }
            repository.saveAndSchedule(all)
            hasLocalEdits = false
            _navigateToAlarmEvent.tryEmit(eventId)
        }
    }

    private suspend fun persistAlarmEvents(setId: Long, events: List<AlarmEvent>) {
        val sorted = events.sortedBy { it.time }
        val all = repository.getAlarmSets().toMutableList()
        val idx = all.indexOfFirst { it.id == setId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(alarmEvents = sorted)
        }
        repository.saveAndSchedule(all)
        _uiState.value = _uiState.value.copy(
            alarmSet = _uiState.value.alarmSet?.copy(alarmEvents = sorted)
        )
    }
}

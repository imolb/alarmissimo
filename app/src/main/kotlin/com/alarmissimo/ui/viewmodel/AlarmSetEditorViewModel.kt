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

/**
 * UI state for the AlarmSet editor.
 *
 * @param alarmSet The alarm-set being edited, or null while loading.
 * @param isSaved Whether the last save/delete operation completed (triggers navigation away).
 */
data class AlarmSetEditorUiState(
    val alarmSet: AlarmSet? = null,
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Alarm-Set Editor screen.
 *
 * Loads the alarm-set by ID, exposes editable state, and persists changes.
 * Subscribes to the DataStore flow so that alarm-event changes made in
 * [AlarmEventEditorViewModel] are picked up when the user navigates back.
 *
 * @param alarmSetId The ID of the alarm-set to edit.
 * @param repository The alarm data repository.
 */
class AlarmSetEditorViewModel(
    private val alarmSetId: Long,
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmSetEditorUiState())
    val uiState: StateFlow<AlarmSetEditorUiState> = _uiState.asStateFlow()

    /**
     * Emits the ID of a newly created / duplicated alarm-event so the screen
     * can navigate to its editor (item 7).
     */
    private val _navigateToAlarmEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToAlarmEvent: SharedFlow<Long> = _navigateToAlarmEvent.asSharedFlow()

    /**
     * Emits the ID of a duplicated alarm-set so the screen can navigate to its editor
     * (item 19: shows "(Kopie)" name immediately in the new set's editor).
     */
    private val _navigateToAlarmSet = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToAlarmSet: SharedFlow<Long> = _navigateToAlarmSet.asSharedFlow()

    /**
     * When true, the user has pending set-level edits (name/enabled/weekdays/volume).
     * In this case, DataStore flow updates only refresh alarmEvents, not the entire set.
     * (Item 11: prevents set-level edits being overwritten by flow emissions.)
     */
    private var hasLocalEdits = false

    init {
        // Subscribe to DataStore so alarm-event changes from AlarmEventEditorViewModel
        // are automatically reflected here (item 11 fix).
        viewModelScope.launch {
            repository.alarmSetsFlow.collect { sets ->
                val refreshed = sets.find { it.id == alarmSetId }
                val current = _uiState.value

                when {
                    current.alarmSet == null -> {
                        // Initial load — use full DataStore state
                        _uiState.value = current.copy(
                            alarmSet = refreshed ?: AlarmSet(
                                id = alarmSetId,
                                name = "",
                                enabled = true,
                                weekdays = (1..7).toList(),
                                audioVolume = 80,
                                alarmEvents = emptyList()
                            )
                        )
                    }

                    refreshed != null && hasLocalEdits -> {
                        // User is editing set-level properties — only sync alarm events
                        // so that edits from AlarmEventEditorViewModel are visible (item 11).
                        val merged = current.alarmSet!!.copy(alarmEvents = refreshed.alarmEvents)
                        _uiState.value = current.copy(alarmSet = merged)
                    }

                    refreshed != null -> {
                        // No local edits yet — keep fully in sync with DataStore
                        _uiState.value = current.copy(alarmSet = refreshed)
                    }
                }
            }
        }
    }

    /**
     * Updates the in-memory alarm-set with [updated].
     * Marks that the user has local edits so subsequent flow updates
     * do not overwrite set-level changes (item 11).
     */
    fun update(updated: AlarmSet) {
        hasLocalEdits = true
        _uiState.value = _uiState.value.copy(alarmSet = updated)
    }

    /**
     * Persists set-level properties (name, enabled, weekdays, audioVolume).
     * Reads the latest alarmEvents from DataStore to avoid losing event-level
     * edits made in [AlarmEventEditorViewModel] (item 11 fix).
     */
    fun save() {
        val localSet = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val idx = all.indexOfFirst { it.id == localSet.id }
            if (idx >= 0) {
                // Keep DataStore's alarmEvents; apply in-memory set-level fields
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

    /** Deletes the current alarm-set. */
    fun delete() {
        viewModelScope.launch {
            val updated = repository.getAlarmSets().filter { it.id != alarmSetId }
            repository.saveAndSchedule(updated)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    /** Duplicates the current alarm-set with new IDs; appends " (Kopie)" to the name.
     *  Navigates to the new copy so its "(Kopie)" name is visible immediately (item 19). */
    fun duplicate() {
        val set = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            // Item 19: same repository function used by ConfigViewModel — always consistent
            val newId = repository.duplicateAlarmSet(set.id) ?: return@launch
            _navigateToAlarmSet.tryEmit(newId)
        }
    }

    /**
     * Adds a new alarm-event to the current alarm-set, saves immediately to DataStore,
     * and signals navigation to the new event's editor (item 7).
     */
    fun addAlarmEvent() {
        val set = _uiState.value.alarmSet ?: return
        val newId = System.currentTimeMillis()
        val newEvent = AlarmEvent(
            id = newId,
            time = "07:00",
            gong = "none",
            timePlayback = true,
            message = ""
        )
        viewModelScope.launch {
            persistAlarmEvents(
                setId = set.id,
                events = (set.alarmEvents + newEvent).sortedBy { it.time }
            )
            _navigateToAlarmEvent.tryEmit(newId)   // Item 7: open editor for new event
        }
    }

    /**
     * Duplicates an alarm-event, saves immediately to DataStore, and signals
     * navigation to the duplicated event (item 7).
     *
     * @param eventId The ID of the alarm-event to duplicate.
     */
    fun duplicateAlarmEvent(eventId: Long) {
        val set = _uiState.value.alarmSet ?: return
        val original = set.alarmEvents.find { it.id == eventId } ?: return
        val newId = System.currentTimeMillis()
        val copy = original.copy(id = newId)
        viewModelScope.launch {
            persistAlarmEvents(
                setId = set.id,
                events = (set.alarmEvents + copy).sortedBy { it.time }
            )
            _navigateToAlarmEvent.tryEmit(newId)   // Item 7: open editor for duplicate
        }
    }

    /**
     * Deletes an alarm-event and saves immediately. Does NOT trigger navigation away
     * from the alarm-set editor (item 8).
     *
     * @param eventId The ID of the alarm-event to delete.
     */
    fun deleteAlarmEvent(eventId: Long) {
        val set = _uiState.value.alarmSet ?: return
        viewModelScope.launch {
            persistAlarmEvents(
                setId = set.id,
                events = set.alarmEvents.filter { it.id != eventId }
            )
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Writes [events] into the stored alarm-set without changing set-level properties,
     * then updates local state.
     */
    private suspend fun persistAlarmEvents(setId: Long, events: List<AlarmEvent>) {
        val sorted = events.sortedBy { it.time }
        val all = repository.getAlarmSets().toMutableList()
        val idx = all.indexOfFirst { it.id == setId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(alarmEvents = sorted)
        }
        repository.saveAndSchedule(all)
        // Local state update: preserves set-level fields
        _uiState.value = _uiState.value.copy(
            alarmSet = _uiState.value.alarmSet?.copy(alarmEvents = sorted)
        )
    }
}

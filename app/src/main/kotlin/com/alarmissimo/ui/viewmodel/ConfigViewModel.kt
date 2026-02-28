package com.alarmissimo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AlarmSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Configuration screen.
 * Exposes the full list of alarm-sets and provides CRUD operations.
 *
 * @param repository The alarm data repository.
 */
class ConfigViewModel(private val repository: AlarmRepository) : ViewModel() {

    /** Complete list of alarm-sets. */
    val alarmSets: StateFlow<List<AlarmSet>> = repository.alarmSetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Emits the ID of a newly created or duplicated alarm-set so the screen can navigate.
     * Item 4 & 10: Open the alarm-set editor immediately after creation.
     */
    private val _navigateToAlarmSet = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToAlarmSet: SharedFlow<Long> = _navigateToAlarmSet.asSharedFlow()

    /**
     * Creates a new empty alarm-set, persists it, then signals navigation to its editor.
     * Item 5: New alarm-sets receive an empty name.
     */
    fun addAlarmSet() {
        viewModelScope.launch {
            val newId = System.currentTimeMillis()
            val current = alarmSets.value.toMutableList()
            current.add(
                AlarmSet(
                    id = newId,
                    name = "",           // Item 5: empty name
                    enabled = true,
                    weekdays = (1..7).toList(),
                    audioVolume = 80,
                    alarmEvents = emptyList()
                )
            )
            repository.saveAndSchedule(current)
            _navigateToAlarmSet.tryEmit(newId)  // Item 4: navigate to new set
        }
    }

    /**
     * Duplicates the alarm-set identified by [id], assigning new IDs,
     * then signals navigation to the new copy's editor.
     * Item 10: Open editor after duplication.
     *
     * @param id The ID of the alarm-set to duplicate.
     */
    fun duplicateAlarmSet(id: Long) {
        viewModelScope.launch {
            // Item 19: delegates to repository's canonical duplicate implementation
            val newId = repository.duplicateAlarmSet(id) ?: return@launch
            _navigateToAlarmSet.tryEmit(newId)  // Item 10: navigate to duplicate
        }
    }

    /**
     * Deletes the alarm-set identified by [id].
     *
     * @param id The ID of the alarm-set to delete.
     */
    fun deleteAlarmSet(id: Long) {
        viewModelScope.launch {
            val updated = alarmSets.value.filter { it.id != id }
            repository.saveAndSchedule(updated)
        }
    }
}

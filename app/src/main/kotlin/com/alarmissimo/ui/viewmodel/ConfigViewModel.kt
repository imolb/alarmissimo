package com.alarmissimo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AlarmSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
     * Creates a new empty alarm-set and persists it.
     */
    fun addAlarmSet() {
        viewModelScope.launch {
            val current = alarmSets.value.toMutableList()
            current.add(
                AlarmSet(
                    id = System.currentTimeMillis(),
                    name = "Neue Weckergruppe",
                    enabled = true,
                    weekdays = (1..7).toList(),
                    audioVolume = 80,
                    alarmEvents = emptyList()
                )
            )
            repository.saveAndSchedule(current)
        }
    }

    /**
     * Duplicates the alarm-set identified by [id], assigning new IDs.
     *
     * @param id The ID of the alarm-set to duplicate.
     */
    fun duplicateAlarmSet(id: Long) {
        viewModelScope.launch {
            val current = alarmSets.value.toMutableList()
            val original = current.find { it.id == id } ?: return@launch
            val copy = original.copy(
                id = System.currentTimeMillis(),
                alarmEvents = original.alarmEvents.map { it.copy(id = System.currentTimeMillis() + it.id % 1000) }
            )
            current.add(copy)
            repository.saveAndSchedule(current)
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

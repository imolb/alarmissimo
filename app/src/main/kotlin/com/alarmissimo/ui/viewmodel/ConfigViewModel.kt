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
 * Exposes the full list of alarm-sets and provides CRUD + enable operations.
 */
class ConfigViewModel(private val repository: AlarmRepository) : ViewModel() {

    val alarmSets: StateFlow<List<AlarmSet>> = repository.alarmSetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _navigateToAlarmSet = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToAlarmSet: SharedFlow<Long> = _navigateToAlarmSet.asSharedFlow()

    fun addAlarmSet() {
        viewModelScope.launch {
            val newId = System.currentTimeMillis()
            val current = alarmSets.value.toMutableList()
            current.add(
                AlarmSet(
                    id = newId,
                    name = "",
                    enabled = true,
                    weekdays = (1..7).toList(),
                    alarmEvents = emptyList()
                )
            )
            repository.saveAndSchedule(current)
            _navigateToAlarmSet.tryEmit(newId)
        }
    }

    fun duplicateAlarmSet(id: Long) {
        viewModelScope.launch {
            val newId = repository.duplicateAlarmSet(id) ?: return@launch
            _navigateToAlarmSet.tryEmit(newId)
        }
    }

    fun deleteAlarmSet(id: Long) {
        viewModelScope.launch {
            val updated = alarmSets.value.filter { it.id != id }
            repository.saveAndSchedule(updated)
        }
    }

    /** Immediately toggles the enabled flag for an alarm-set. */
    fun setAlarmSetEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmSetEnabled(id, enabled)
        }
    }
}

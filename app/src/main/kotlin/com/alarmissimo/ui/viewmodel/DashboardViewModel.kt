package com.alarmissimo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A single item displayed on the dashboard. */
data class UpcomingAlarm(
    val alarmSet: AlarmSet,
    val alarmEvent: AlarmEvent,
    val triggerMillis: Long
)

/**
 * ViewModel for the Dashboard screen.
 * Exposes the list of alarm-events firing in the next 24 hours, sorted by time.
 * Updates every second so the countdown display (item 16) stays accurate.
 *
 * @param repository The alarm data repository.
 */
class DashboardViewModel(private val repository: AlarmRepository) : ViewModel() {

    /** Emits the current time in millis every second for countdown updates (items 16 & 21). */
    private val _tickMillis = MutableStateFlow(System.currentTimeMillis())

    /** Public ticker so the Dashboard composable can recompose countdown text every second. */
    val tickMillis: StateFlow<Long> = _tickMillis

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                _tickMillis.value = System.currentTimeMillis()
            }
        }
    }

    /** Upcoming alarms within the next 24 hours, sorted earliest first. */
    val upcomingAlarms: StateFlow<List<UpcomingAlarm>> =
        combine(repository.alarmSetsFlow, _tickMillis) { sets, _ ->
            buildUpcomingList(sets)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildUpcomingList(sets: List<AlarmSet>): List<UpcomingAlarm> {
        val horizon = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
        return sets.filter { it.enabled }
            .flatMap { set ->
                set.alarmEvents.mapNotNull { event ->
                    val t = TimeUtils.nextOccurrenceMillis(event.time, set.weekdays)
                        ?: return@mapNotNull null
                    if (t > horizon) return@mapNotNull null
                    UpcomingAlarm(set, event, t)
                }
            }
            .sortedBy { it.triggerMillis }
    }
}

package com.alarmissimo.data.model

import kotlinx.serialization.Serializable

/**
 * A named group of alarm events that share a schedule (weekdays, volume).
 *
 * @param id Auto-generated identifier (epoch milliseconds).
 * @param name Display name, 1–30 characters.
 * @param enabled Whether the alarm-set is active.
 * @param weekdays Days on which alarms fire (1=Mon … 7=Sun, Calendar convention).
 * @param audioVolume Playback volume, 0–100.
 * @param alarmEvents The individual timed alarms within this set.
 */
@Serializable
data class AlarmSet(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val weekdays: List<Int>,
    val audioVolume: Int,
    val alarmEvents: List<AlarmEvent>
)

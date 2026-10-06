package com.alarmissimo.data.model

import kotlinx.serialization.Serializable

/**
 * A named group of alarm events that share a schedule.
 *
 * @param id Auto-generated identifier (epoch milliseconds).
 * @param name Display name, 1–30 characters.
 * @param enabled Whether the alarm-set is active.
 * @param weekdays Days on which alarms fire (1=Mon … 7=Sun, Calendar convention).
 *   Ignored when [specificDate] is set.
 * @param alarmEvents The individual timed alarms within this set.
 * @param specificDate ISO date string ("yyyy-MM-dd") for a one-off alarm set, or null
 *   to use weekly [weekdays] repetition.
 * @param timeMode "absolute" (events have explicit [AlarmEvent.time]) or
 *   "relative" (events fire [AlarmEvent.offsetMinutes] before [endTime]).
 * @param endTime End time in "HH:mm" format used when [timeMode] = "relative",
 *   or null in absolute mode.
 * @param endEventName Human-readable name for the end event (up to 50 chars),
 *   used in duration-playback TTS. Only relevant when [timeMode] = "relative".
 * @param audioVolume Playback volume for both gong and TTS, 0–100.
 */
@Serializable
data class AlarmSet(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val weekdays: List<Int>,
    val alarmEvents: List<AlarmEvent>,
    val specificDate: String? = null,
    val timeMode: String = "absolute",
    val endTime: String? = null,
    val endEventName: String = "",
    val audioVolume: Int = 80
)

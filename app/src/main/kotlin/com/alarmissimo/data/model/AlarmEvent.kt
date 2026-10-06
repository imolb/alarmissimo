package com.alarmissimo.data.model

import kotlinx.serialization.Serializable

/**
 * A single timed alarm within an [AlarmSet].
 *
 * @param id Auto-generated identifier (epoch milliseconds).
 * @param time Scheduled time in "HH:mm" format (absolute mode) or used as the
 *   computed trigger time cache (relative mode — not stored, derived from [offsetMinutes]).
 * @param gong Gong sound identifier: "bikebell1x", "bikebell2x", "gong1x", "gong2x",
 *   "gong3x", "gong4x", "doorbell", "kettle", or "none".
 * @param timePlayback Whether to announce the time in German via TTS.
 * @param message Text-to-speech message spoken after the gong (empty = skip).
 * @param enabled Whether this event is active. When false the alarm does not fire.
 * @param offsetMinutes Minutes before [AlarmSet.endTime] (relative mode). 0 = at endTime.
 * @param voiceProfileId ID of the [VoiceProfile] to use for TTS. 0 = Standard profile.
 * @param durationPlayback When true (and alarm-set timeMode is "relative"), TTS announces
 *   "Es sind noch <offsetMinutes> Minuten bis <endEventName>".
 */
@Serializable
data class AlarmEvent(
    val id: Long,
    val time: String,
    val gong: String,
    val timePlayback: Boolean,
    val message: String,
    val enabled: Boolean = true,
    val offsetMinutes: Int = 0,
    val voiceProfileId: Long = 0L,
    val durationPlayback: Boolean = false
)

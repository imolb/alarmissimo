package com.alarmissimo.data.model

import kotlinx.serialization.Serializable

/**
 * A single timed alarm within an [AlarmSet].
 *
 * @param id Auto-generated identifier (epoch milliseconds).
 * @param time Scheduled time in "HH:mm" format.
 * @param gong Gong sound identifier: "bikebell", "doorbell", "kettle", "gong", or "none".
 * @param timePlayback Whether to announce the time in German via TTS.
 * @param message Text-to-speech message spoken after the gong (empty = skip).
 */
@Serializable
data class AlarmEvent(
    val id: Long,
    val time: String,
    val gong: String,
    val timePlayback: Boolean,
    val message: String
)

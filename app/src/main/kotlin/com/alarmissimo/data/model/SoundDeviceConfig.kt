package com.alarmissimo.data.model

import kotlinx.serialization.Serializable

/**
 * A Bluetooth device that has been accepted as the preferred sound output.
 *
 * @param mac Bluetooth MAC address used for identity checks.
 * @param name Human-readable display name (last known, may be stale).
 */
@Serializable
data class AcceptedDevice(
    val mac: String,
    val name: String
)

/**
 * Global configuration for sound device management.
 *
 * @param btWarningEnabled When true, a notification fires before each alarm if none of
 *   [acceptedDevices] is currently the active BT audio output.
 * @param btWarningAheadMinutes How many minutes before the alarm the warning notification fires.
 * @param acceptedDevices List of Bluetooth devices that satisfy the requirement.
 * @param btKeepAliveEnabled When true, a 3-second silent audio clip is played every
 *   [btKeepAliveIntervalMinutes] minutes to keep the BT connection alive.
 * @param btKeepAliveIntervalMinutes Interval in minutes between keep-alive pings.
 * @param btAutoDisconnectEnabled When true, BT A2DP is disconnected if no alarm fires
 *   within the next [btAutoDisconnectAheadMinutes] minutes.
 * @param btAutoDisconnectAheadMinutes Lookahead window in minutes (1–120).
 */
@Serializable
data class SoundDeviceConfig(
    val btWarningEnabled: Boolean = false,
    val btWarningAheadMinutes: Int = 10,
    val acceptedDevices: List<AcceptedDevice> = emptyList(),
    val btKeepAliveEnabled: Boolean = false,
    val btKeepAliveIntervalMinutes: Int = 10,
    val btAutoDisconnectEnabled: Boolean = false,
    val btAutoDisconnectAheadMinutes: Int = 30
)

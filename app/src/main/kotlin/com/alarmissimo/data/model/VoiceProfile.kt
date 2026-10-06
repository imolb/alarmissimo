package com.alarmissimo.data.model

import kotlinx.serialization.Serializable

/**
 * A named TTS voice configuration.
 *
 * The profile with [id] = 0 is the built-in "Standard" profile, which is never
 * stored in DataStore — it is a compile-time constant ([STANDARD_PROFILE]).
 *
 * @param id Unique identifier (epoch milliseconds for user-created profiles). 0 = Standard.
 * @param name Display name shown in the voice list.
 * @param speechRate TTS speech rate multiplier (1.0 = normal).
 * @param pitch TTS pitch multiplier (1.0 = normal).
 * @param pan Stereo pan: -1.0 (left) … 0.0 (center) … 1.0 (right).
 * @param language BCP-47 language tag ("de-DE", "en-US", …) or "system" to follow device locale.
 * @param voiceName Specific TTS voice name, or null to use engine default for [language].
 * @param enginePackage TTS engine package name, or null to use system default.
 */
@Serializable
data class VoiceProfile(
    val id: Long,
    val name: String,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val pan: Float = 0.0f,
    val language: String = "system",
    val voiceName: String? = null,
    val enginePackage: String? = null
) {
    companion object {
        /** Built-in Standard profile. id=0, never persisted in DataStore. */
        val STANDARD_PROFILE = VoiceProfile(
            id = 0L,
            name = "Standard",
            speechRate = 1.0f,
            pitch = 1.0f,
            pan = 0.0f,
            language = "system",
            voiceName = null,
            enginePackage = null
        )
    }
}

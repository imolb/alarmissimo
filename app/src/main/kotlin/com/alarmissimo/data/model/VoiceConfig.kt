package com.alarmissimo.data.model

import kotlinx.serialization.Serializable

/**
 * Global TTS voice configuration used by the Voice Configuration screen and
 * applied whenever "Jetzt abspielen" is triggered from that screen.
 *
 * These settings are persisted via DataStore and are independent of individual
 * alarm-sets (which have their own [AlarmSet.audioVolume]).
 *
 * @param speechRate Android `TextToSpeech.setSpeechRate` value. 1.0 = normal speed.
 *   Range: 0.5 – 2.0.
 * @param pitch Android `TextToSpeech.setPitch` value. 1.0 = normal pitch.
 *   Range: 0.5 – 2.0.
 * @param pan Stereo pan passed via `KEY_PARAM_PAN`. 0.0 = centre, -1.0 = full left,
 *   +1.0 = full right.
 * @param volume Output volume passed via `KEY_PARAM_VOLUME`, 0–100.
 * @param language BCP-47 language tag, e.g. "de-DE", "en-US", or "system" for device
 *   default.
 * @param voiceName Name of a specific [android.speech.tts.Voice] to use, or `null` to
 *   let the engine choose.
 * @param enginePackage Package name of the TTS engine to use, or `null` for the system
 *   default engine.
 */
@Serializable
data class VoiceConfig(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val pan: Float = 0.0f,
    val volume: Int = 80,
    val language: String = "de-DE",
    val voiceName: String? = null,
    val enginePackage: String? = null
)

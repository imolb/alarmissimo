package com.alarmissimo.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Minimal info about an installed TTS engine, for display in the engine dropdown.
 *
 * @param packageName Unique package name used as the identifier.
 * @param label Human-readable engine name.
 */
data class EngineInfo(val packageName: String, val label: String)

/**
 * Minimal info about a single [android.speech.tts.Voice], for display in the voice dropdown.
 *
 * @param name Unique voice name (as returned by [android.speech.tts.Voice.getName]).
 * @param displayLabel Human-readable label shown in the UI.
 * @param languageTag BCP-47 language tag of the voice's locale (e.g. "de-DE"), used to
 *   filter the dropdown when a language is selected.
 */
data class VoiceInfo(val name: String, val displayLabel: String, val languageTag: String)

/**
 * UI state for the Voice Configuration screen.
 *
 * @param config The current (in-memory, possibly unsaved) voice configuration.
 * @param availableEngines TTS engines discovered on the device.
 * @param availableVoices Voices provided by the currently selected (or default) engine.
 * @param isPlaying Whether a TTS preview utterance is currently running.
 */
data class VoiceConfigUiState(
    val config: VoiceConfig = VoiceConfig(),
    val availableEngines: List<EngineInfo> = emptyList(),
    val availableVoices: List<VoiceInfo> = emptyList(),
    val isPlaying: Boolean = false
)

/**
 * ViewModel for the Voice Configuration screen.
 *
 * Loads/saves [VoiceConfig] via [AlarmRepository], enumerates TTS engines and voices,
 * and handles the "Jetzt abspielen" preview with current settings.
 */
class VoiceConfigViewModel(
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceConfigUiState())
    val uiState: StateFlow<VoiceConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.voiceConfigFlow.collect { config ->
                _uiState.value = _uiState.value.copy(config = config)
            }
        }
    }

    /** Replaces the in-memory config. Call [save] to persist. */
    fun update(config: VoiceConfig) {
        _uiState.value = _uiState.value.copy(config = config)
    }

    /** Persists the current in-memory config. */
    fun save() {
        viewModelScope.launch {
            repository.saveVoiceConfig(_uiState.value.config)
        }
    }

    /**
     * Discovers TTS engines via [PackageManager] and voices via a temporary
     * [TextToSpeech] instance. Call from the screen's [LaunchedEffect].
     */
    fun loadEnginesAndVoices(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // --- Enumerate engines ---
            val pm = context.packageManager
            val ttsIntent = Intent("android.intent.action.TTS_SERVICE")
            @Suppress("DEPRECATION")
            val services = pm.queryIntentServices(ttsIntent, 0)
            val engines = services.map { ri ->
                val label = ri.serviceInfo.loadLabel(pm).toString()
                EngineInfo(packageName = ri.serviceInfo.packageName, label = label)
            }
            _uiState.value = _uiState.value.copy(availableEngines = engines)

            // --- Enumerate voices via a temporary TTS instance ---
            val enginePkg = _uiState.value.config.enginePackage
            val voices = mutableListOf<VoiceInfo>()

            // Block until TTS is ready (or fails) using a simple CountDownLatch equivalent
            val latch = java.util.concurrent.CountDownLatch(1)
            var tts: TextToSpeech? = null
            val initCallback = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.voices?.sortedWith(
                        compareBy({ it.locale.toLanguageTag() }, { it.name })
                    )?.forEach { v ->
                        val lang = v.locale.getDisplayName(Locale.GERMAN)
                        voices.add(
                            VoiceInfo(
                                name = v.name,
                                displayLabel = "$lang — ${v.name}",
                                languageTag = v.locale.toLanguageTag()
                            )
                        )
                    }
                }
                latch.countDown()
            }
            @Suppress("UsePropertyAccessSyntax")
            tts = if (enginePkg != null) {
                TextToSpeech(context, initCallback, enginePkg)
            } else {
                TextToSpeech(context, initCallback)
            }
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            tts?.shutdown()

            _uiState.value = _uiState.value.copy(availableVoices = voices)
        }
    }

    /**
     * Speaks [text] using all current voice settings for a live preview.
     *
     * The [isPlaying] flag in [uiState] is set true while speaking and reset when done.
     */
    fun playNow(context: Context, text: String) {
        if (_uiState.value.isPlaying) return
        val config = _uiState.value.config
        _uiState.value = _uiState.value.copy(isPlaying = true)

        viewModelScope.launch(Dispatchers.IO) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var tts: TextToSpeech? = null
            val initCallback = TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.SUCCESS) {
                    latch.countDown()
                    return@OnInitListener
                }
                // Language
                val locale = if (config.language == "system") {
                    Locale.getDefault()
                } else {
                    Locale.forLanguageTag(config.language)
                }
                tts?.setLanguage(locale)

                // Specific voice (if selected and still available)
                if (config.voiceName != null) {
                    tts?.voices?.find { it.name == config.voiceName }?.let { tts?.setVoice(it) }
                }

                // Speech rate + pitch
                tts?.setSpeechRate(config.speechRate)
                tts?.setPitch(config.pitch)

                // Utterance listener to know when speech is done
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { latch.countDown() }
                    override fun onError(utteranceId: String?) { latch.countDown() }
                })

                // Bundle params: volume + pan
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.volume / 100f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, config.pan)
                }
                tts?.speak(text.ifBlank { "Testausgabe" }, TextToSpeech.QUEUE_FLUSH, params, "preview")
            }

            tts = if (config.enginePackage != null) {
                TextToSpeech(context, initCallback, config.enginePackage)
            } else {
                TextToSpeech(context, initCallback)
            }

            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
            tts?.stop()
            tts?.shutdown()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }
    }
}

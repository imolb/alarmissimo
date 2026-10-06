package com.alarmissimo.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Minimal info about an installed TTS engine. */
data class EngineInfo(val packageName: String, val label: String)

/** Minimal info about a TTS voice for display in a dropdown. */
data class VoiceInfo(val name: String, val displayLabel: String, val languageTag: String)

/** UI state for the Voice Profile editor screen. */
data class VoiceProfileUiState(
    val profile: VoiceProfile = VoiceProfile(id = 0L, name = ""),
    val availableEngines: List<EngineInfo> = emptyList(),
    val availableVoices: List<VoiceInfo> = emptyList(),
    val isPlaying: Boolean = false,
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Voice Profile editor screen.
 *
 * Back-saves on [saveAndClose]. No explicit Save button.
 */
class VoiceProfileViewModel(
    private val profileId: Long,
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceProfileUiState())
    val uiState: StateFlow<VoiceProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val all = repository.getAllVoiceProfiles()
            val profile = all.find { it.id == profileId }
                ?: VoiceProfile(id = System.currentTimeMillis(), name = "")
            _uiState.value = _uiState.value.copy(profile = profile)
        }
    }

    fun update(profile: VoiceProfile) {
        _uiState.value = _uiState.value.copy(profile = profile)
    }

    /** Persists and signals navigation back. Standard profile (id=0) is read-only; save is no-op. */
    fun saveAndClose() {
        val profile = _uiState.value.profile
        if (profile.id == 0L) {
            _uiState.value = _uiState.value.copy(isSaved = true)
            return
        }
        viewModelScope.launch {
            repository.updateVoiceProfile(profile)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun delete() {
        val profile = _uiState.value.profile
        if (profile.id == 0L) return  // cannot delete Standard
        viewModelScope.launch {
            repository.deleteVoiceProfile(profile.id)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    /** Enumerates installed TTS engines and voices. Call from a LaunchedEffect. */
    fun loadEnginesAndVoices(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Engines
            val pm = context.packageManager
            val ttsIntent = Intent("android.intent.action.TTS_SERVICE")
            @Suppress("DEPRECATION")
            val services = pm.queryIntentServices(ttsIntent, 0)
            val engines = services.map { ri ->
                EngineInfo(ri.serviceInfo.packageName, ri.serviceInfo.loadLabel(pm).toString())
            }
            _uiState.value = _uiState.value.copy(availableEngines = engines)

            // Voices via a temporary TTS instance
            val enginePkg = _uiState.value.profile.enginePackage
            val voices = mutableListOf<VoiceInfo>()
            val latch = java.util.concurrent.CountDownLatch(1)
            var tts: TextToSpeech? = null
            tts = (if (enginePkg != null) TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.voices?.sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
                        ?.forEach { v ->
                            val lang = v.locale.getDisplayName(Locale.GERMAN)
                            voices.add(VoiceInfo(v.name, "$lang — ${v.name}", v.locale.toLanguageTag()))
                        }
                }
                latch.countDown()
            }, enginePkg) else TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.voices?.sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
                        ?.forEach { v ->
                            val lang = v.locale.getDisplayName(Locale.GERMAN)
                            voices.add(VoiceInfo(v.name, "$lang — ${v.name}", v.locale.toLanguageTag()))
                        }
                }
                latch.countDown()
            })
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            tts.shutdown()
            _uiState.value = _uiState.value.copy(availableVoices = voices)
        }
    }

    /** Speaks [text] using current profile settings as a live preview. */
    fun playNow(context: Context, text: String) {
        if (_uiState.value.isPlaying) return
        val profile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(isPlaying = true)
        viewModelScope.launch(Dispatchers.IO) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var tts: TextToSpeech? = null
            val cb = TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.SUCCESS) { latch.countDown(); return@OnInitListener }
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                val locale = if (profile.language == "system") Locale.getDefault()
                             else Locale.forLanguageTag(profile.language)
                tts?.setLanguage(locale)
                if (profile.voiceName != null) {
                    tts?.voices?.find { it.name == profile.voiceName }?.let { tts?.setVoice(it) }
                }
                tts?.setSpeechRate(profile.speechRate)
                tts?.setPitch(profile.pitch)
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(u: String?) {}
                    override fun onDone(u: String?) { latch.countDown() }
                    override fun onError(u: String?) { latch.countDown() }
                })
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, profile.pan)
                }
                tts?.speak(text.ifBlank { "Testausgabe" }, TextToSpeech.QUEUE_FLUSH, params, "preview")
            }
            tts = if (profile.enginePackage != null) TextToSpeech(context, cb, profile.enginePackage)
                  else TextToSpeech(context, cb)
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
            tts?.stop(); tts?.shutdown()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }
    }
}

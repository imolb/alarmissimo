package com.alarmissimo.ui.viewmodel

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import com.alarmissimo.data.model.VoiceProfile
import com.alarmissimo.service.AlarmPlaybackHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlarmEventEditorUiState(
    val alarmEvent: AlarmEvent? = null,
    val alarmSet: AlarmSet? = null,
    val availableProfiles: List<VoiceProfile> = emptyList(),
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Alarm-Event Editor screen.
 *
 * Back-navigation auto-saves. Exposes [availableProfiles] so the
 * screen can show a voice-profile dropdown.
 */
class AlarmEventEditorViewModel(
    private val alarmSetId: Long,
    private val alarmEventId: Long,
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmEventEditorUiState())
    val uiState: StateFlow<AlarmEventEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val sets = repository.getAlarmSets()
            val alarmSet = sets.find { it.id == alarmSetId }
            val event = alarmSet?.alarmEvents?.find { it.id == alarmEventId }
                ?: AlarmEvent(id = System.currentTimeMillis(), time = "07:00", gong = "none", timePlayback = true, message = "")
            val profiles = repository.getAllVoiceProfiles()
            _uiState.value = AlarmEventEditorUiState(alarmEvent = event, alarmSet = alarmSet, availableProfiles = profiles)
        }
    }

    fun update(updated: AlarmEvent) {
        _uiState.value = _uiState.value.copy(alarmEvent = updated)
    }

    /** Persists and triggers navigation back (back-saves). */
    fun save() {
        val event = _uiState.value.alarmEvent ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val setIdx = all.indexOfFirst { it.id == alarmSetId }
            if (setIdx < 0) return@launch
            val set = all[setIdx]
            val events = set.alarmEvents.toMutableList()
            val eIdx = events.indexOfFirst { it.id == event.id }
            if (eIdx >= 0) events[eIdx] = event else events.add(event)
            all[setIdx] = set.copy(alarmEvents = events.sortedBy { it.time })
            repository.saveAndSchedule(all)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun delete() {
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val setIdx = all.indexOfFirst { it.id == alarmSetId }
            if (setIdx < 0) return@launch
            val set = all[setIdx]
            all[setIdx] = set.copy(alarmEvents = set.alarmEvents.filter { it.id != alarmEventId })
            repository.saveAndSchedule(all)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun duplicate() {
        val event = _uiState.value.alarmEvent ?: return
        viewModelScope.launch {
            val all = repository.getAlarmSets().toMutableList()
            val setIdx = all.indexOfFirst { it.id == alarmSetId }
            if (setIdx < 0) return@launch
            val set = all[setIdx]
            val copy = event.copy(id = System.currentTimeMillis())
            all[setIdx] = set.copy(alarmEvents = (set.alarmEvents + copy).sortedBy { it.time })
            repository.saveAndSchedule(all)
        }
    }

    fun playNow(context: Context) {
        val event = _uiState.value.alarmEvent ?: return
        val profiles = _uiState.value.availableProfiles
        val voiceProfile = profiles.find { it.id == event.voiceProfileId } ?: VoiceProfile.STANDARD_PROFILE
        val endEventName = _uiState.value.alarmSet?.endEventName ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val useBt = audioManager.isBluetoothA2dpOn()
            val audioStream = if (useBt) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
            val savedVolume = audioManager.getStreamVolume(audioStream)
            val maxVolume   = audioManager.getStreamMaxVolume(audioStream)
            try {
                audioManager.setStreamVolume(audioStream,
                    (maxVolume * (_uiState.value.alarmSet?.audioVolume ?: 80) / 100.0).toInt().coerceIn(0, maxVolume), 0)
                AlarmPlaybackHelper(context).play(event, voiceProfile, endEventName, useBluetooth = useBt)
            } finally {
                audioManager.setStreamVolume(audioStream, savedVolume, 0)
            }
        }
    }
}

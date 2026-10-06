package com.alarmissimo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.VoiceProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Voice List screen.
 *
 * Exposes all profiles (Standard first, then user-created) for display.
 */
class VoiceListViewModel(private val repository: AlarmRepository) : ViewModel() {

    /** All profiles: Standard profile (id=0) prepended to user-created profiles. */
    val profiles: StateFlow<List<VoiceProfile>> = repository.voiceProfilesFlow
        .map { userProfiles -> listOf(VoiceProfile.STANDARD_PROFILE) + userProfiles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(VoiceProfile.STANDARD_PROFILE))

    private val _navigateToProfile = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToProfile: SharedFlow<Long> = _navigateToProfile.asSharedFlow()

    /** Creates a new empty profile and navigates to its editor. */
    fun addProfile() {
        viewModelScope.launch {
            val newId = System.currentTimeMillis()
            val newProfile = VoiceProfile(id = newId, name = "")
            repository.addVoiceProfile(newProfile)
            _navigateToProfile.tryEmit(newId)
        }
    }

    /** Duplicates [profileId] (works for id=0 Standard) and navigates to the copy. */
    fun duplicateProfile(profileId: Long) {
        viewModelScope.launch {
            val newId = repository.duplicateVoiceProfile(profileId) ?: return@launch
            _navigateToProfile.tryEmit(newId)
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteVoiceProfile(profileId)
        }
    }
}

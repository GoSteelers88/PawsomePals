package io.pawsomepals.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pawsomepals.app.data.model.Settings
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _settingsState = MutableStateFlow<SettingsState>(SettingsState.Loading)
    val settingsState: StateFlow<SettingsState> = _settingsState

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings()
                _settingsState.value = SettingsState.Success(settings)
            } catch (e: Exception) {
                _settingsState.value = SettingsState.Error("Failed to load settings: ${e.message}")
            }
        }
    }

    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            try {
                settingsRepository.updateSettings(newSettings)
                _settingsState.value = SettingsState.Success(newSettings)
            } catch (e: Exception) {
                _settingsState.value = SettingsState.Error("Failed to update settings: ${e.message}")
            }
        }
    }

    fun updatePrivacySetting(setting: String, value: Boolean) {
        val currentSettings = (_settingsState.value as? SettingsState.Success)?.settings ?: return
        val updatedSettings = when (setting) {
            "profileVisibility" -> currentSettings.copy(profileVisibility = value)
            "locationSharing" -> currentSettings.copy(locationSharing = value)
            "dataUsage" -> currentSettings.copy(dataUsage = value)
            else -> currentSettings
        }
        updateSettings(updatedSettings)
    }

    fun updateNotificationPreference(preference: String, value: Boolean) {
        val currentSettings = (_settingsState.value as? SettingsState.Success)?.settings ?: return
        val updatedSettings = when (preference) {
            "newMatches" -> currentSettings.copy(newMatchesNotification = value)
            "messages" -> currentSettings.copy(messagesNotification = value)
            "playdateReminders" -> currentSettings.copy(playdateRemindersNotification = value)
            "appUpdates" -> currentSettings.copy(appUpdatesNotification = value)
            else -> currentSettings
        }
        updateSettings(updatedSettings)
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                authRepository.deleteAccount()
                // Handle successful account deletion (e.g., navigate to login screen)
            } catch (e: Exception) {
                // Handle error
                _settingsState.value = SettingsState.Error("Failed to delete account: ${e.message}")
            }
        }
    }



    sealed class SettingsState {
        object Loading : SettingsState()
        data class Success(val settings: Settings) : SettingsState()
        data class Error(val message: String) : SettingsState()
    }
}
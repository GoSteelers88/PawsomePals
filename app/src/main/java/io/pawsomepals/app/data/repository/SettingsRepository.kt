
package io.pawsomepals.app.data.repository

import android.location.Location
import io.pawsomepals.app.data.dao.SettingsDao
import io.pawsomepals.app.data.model.Settings
import io.pawsomepals.app.data.remote.SettingsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val settingsApi: SettingsApi
) {

    private suspend fun ensureSettingsExist() {
        if (settingsDao.getSettings() == null) {
            settingsDao.insertSettings(Settings())
        }
    }
    fun getSettingsFlow(): Flow<Settings> =
        settingsDao.getSettingsFlow()
            .transform { settings ->
                emit(settings ?: Settings())
            }

    suspend fun getPreferredEnergyLevels(): List<String> {
        return getSettings().breedPreferences
    }

    suspend fun getPreferredAgeRange(): ClosedRange<Int> {
        val settings = getSettings()
        return settings.agePreferenceMin..settings.agePreferenceMax
    }

    data class ProfileFilters(
        val maxDistance: Double,
        val energyLevels: List<String>,
        val ageRange: ClosedRange<Int>,
        val currentLocation: Location?
    )

    suspend fun getSettings(): Settings {
        ensureSettingsExist()
        return try {
            // Fetch latest settings from the API
            val remoteSettings = settingsApi.fetchSettings()
            // Update local database
            settingsDao.updateOrInsertSettings(remoteSettings)
            remoteSettings
        } catch (e: Exception) {
            // If API call fails, return local settings or default Settings object
            settingsDao.getSettings() ?: Settings()
        }
    }


    suspend fun updateSettings(newSettings: Settings) {
        // Update local database
        settingsDao.updateOrInsertSettings(newSettings)
        try {
            // Sync with remote API
            settingsApi.updateSettings(newSettings)
        } catch (e: Exception) {
            // Log error but don't fail since local update succeeded
            e.printStackTrace()
        }
    }

    // Convenience methods for common settings updates
    suspend fun updateMaxDistance(distance: Int) {
        val current = getSettings()
        updateSettings(current.copy(maxDistance = distance))
    }

    suspend fun updateBreedPreferences(breeds: List<String>) {
        val current = getSettings()
        updateSettings(current.copy(breedPreferences = breeds))
    }

    suspend fun updateAgePreferences(min: Int, max: Int) {
        val current = getSettings()
        updateSettings(current.copy(
            agePreferenceMin = min,
            agePreferenceMax = max
        ))
    }

    suspend fun updateNotificationPreferences(
        matches: Boolean? = null,
        messages: Boolean? = null,
        playdates: Boolean? = null,
        appUpdates: Boolean? = null
    ) {
        val current = getSettings()
        updateSettings(
            current.copy(
                newMatchesNotification = matches ?: current.newMatchesNotification,
                messagesNotification = messages ?: current.messagesNotification,
                playdateRemindersNotification = playdates ?: current.playdateRemindersNotification,
                appUpdatesNotification = appUpdates ?: current.appUpdatesNotification
            )
        )
    }

    suspend fun updatePrivacySettings(
        privacyLevel: Settings.PrivacyLevel? = null,
        profileVisibility: Boolean? = null,
        locationSharing: Boolean? = null
    ) {
        val current = getSettings()
        updateSettings(
            current.copy(
                privacyLevel = privacyLevel ?: current.privacyLevel,
                profileVisibility = profileVisibility ?: current.profileVisibility,
                locationSharing = locationSharing ?: current.locationSharing
            )
        )
    }

    suspend fun updateDisplayPreferences(
        darkMode: Boolean? = null,
        language: String? = null
    ) {
        val current = getSettings()
        updateSettings(
            current.copy(
                darkModeEnabled = darkMode ?: current.darkModeEnabled,
                languageCode = language ?: current.languageCode
            )
        )
    }

    suspend fun toggleNotifications(enabled: Boolean) {
        val current = getSettings()
        updateSettings(current.copy(notificationsEnabled = enabled))
    }

    suspend fun toggleDataUsage(enabled: Boolean) {
        val current = getSettings()
        updateSettings(current.copy(dataUsage = enabled))
    }
}

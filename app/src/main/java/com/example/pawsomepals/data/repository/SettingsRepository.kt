package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.dao.SettingsDao
import com.example.pawsomepals.data.model.Settings
import com.example.pawsomepals.data.remote.SettingsApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val settingsApi: SettingsApi
) {
    fun getSettingsFlow(): Flow<Settings> = settingsDao.getSettingsFlow()

    suspend fun getSettings(): Settings {
        return try {
            // Fetch latest settings from the API
            val remoteSettings = settingsApi.fetchSettings()
            // Update local database
            settingsDao.updateOrInsertSettings(remoteSettings)
            remoteSettings
        } catch (e: Exception) {
            // If API call fails, return local settings or a default Settings object
            settingsDao.getSettings() ?: Settings() // Assuming Settings has a no-arg constructor for default values
        }
    }

    suspend fun updateSettings(newSettings: Settings) {
        // Update local database
        settingsDao.updateOrInsertSettings(newSettings)
        try {
            // Sync with remote API
            settingsApi.updateSettings(newSettings)
        } catch (e: Exception) {
            // If API call fails, we've already updated local DB, so just log the error
            e.printStackTrace()
        }
    }
}
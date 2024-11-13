package io.pawsomepals.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.pawsomepals.app.data.model.SafetyChecklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // Core user preferences
    suspend fun isUserLoggedIn(): Boolean {
        return dataStore.data.first()[IS_LOGGED_IN] ?: false
    }

    suspend fun setUserLoggedIn(isLoggedIn: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = isLoggedIn
        }
    }

    // Dog related preferences
    suspend fun getCurrentDogId(): String? {
        return dataStore.data.first()[CURRENT_DOG_ID]
    }

    suspend fun setCurrentDogId(dogId: String) {
        dataStore.edit { preferences ->
            preferences[CURRENT_DOG_ID] = dogId
        }
    }

    // App settings
    suspend fun getCalendarSync(): Boolean {
        return dataStore.data.first()[CALENDAR_SYNC] ?: false
    }

    suspend fun setCalendarSync(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CALENDAR_SYNC] = enabled
        }
    }

    // Safety checklist
    suspend fun saveSafetyChecklist(checklist: SafetyChecklist) {
        dataStore.edit { preferences ->
            preferences[SAFETY_CHECKLIST_COMPLETED] =
                checklist.vaccinationVerified &&
                        checklist.sizeCompatible &&
                        checklist.energyLevelMatched &&
                        checklist.meetingSpotConfirmed &&
                        checklist.backupContactShared
        }
    }

    // Flow getters for reactive updates
    val isUserLoggedInFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[IS_LOGGED_IN] ?: false
        }

    val currentDogIdFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[CURRENT_DOG_ID]
        }

    val calendarSyncFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[CALENDAR_SYNC] ?: false
        }

    val safetyChecklistFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SAFETY_CHECKLIST_COMPLETED] ?: false
        }

    companion object {
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val CURRENT_DOG_ID = stringPreferencesKey("current_dog_id")
        private val CALENDAR_SYNC = booleanPreferencesKey("calendar_sync")
        private val SAFETY_CHECKLIST_COMPLETED = booleanPreferencesKey("safety_checklist_completed")
    }
}
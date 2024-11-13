package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.pawsomepals.app.data.converters.Converters

@Entity(tableName = "settings")
@TypeConverters(Converters::class)
data class Settings(
    @PrimaryKey val id: Int = 1,
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val privacyLevel: PrivacyLevel = PrivacyLevel.MEDIUM,
    val maxDistance: Int = 50,
    val agePreferenceMin: Int = 0,
    val agePreferenceMax: Int = 20,
    val breedPreferences: List<String> = emptyList(),
    val languageCode: String = "en",
    val profileVisibility: Boolean = true,
    val locationSharing: Boolean = false,
    val dataUsage: Boolean = true,
    val newMatchesNotification: Boolean = true,
    val messagesNotification: Boolean = true,
    val playdateRemindersNotification: Boolean = true,
    val appUpdatesNotification: Boolean = true
) {
    enum class PrivacyLevel {
        LOW, MEDIUM, HIGH
    }
}
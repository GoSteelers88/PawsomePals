package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.pawsomepals.data.Converters
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
@Entity(tableName = "matches")
@TypeConverters(Converters::class)
data class Match(
    @PrimaryKey
    val id: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val dog1Id: String = "",
    val dog2Id: String = "",


    // Match details
    val compatibilityScore: Double = 0.0,
    val status: MatchStatus = MatchStatus.PENDING,
    @TypeConverters(Converters::class)
    val matchReasons: List<MatchReason> = emptyList(),

    // Timestamps
    val timestamp: Long = System.currentTimeMillis(),
    val lastInteractionTimestamp: Long = System.currentTimeMillis(),
    val expiryTimestamp: Long = System.currentTimeMillis() + DEFAULT_EXPIRY_DURATION,

    // Match preferences
    val preferredPlaydateLocation: String? = null,
    val preferredPlaydateTime: String? = null,

    // Interaction flags
    val isArchived: Boolean = false,
    val isHidden: Boolean = false,
    val hasUnreadMessages: Boolean = false
) {
    companion object {
        const val DEFAULT_EXPIRY_DURATION = 7 * 24 * 60 * 60 * 1000L // 7 days in milliseconds
    }

    // Helper functions
    fun isExpired(): Boolean = System.currentTimeMillis() > expiryTimestamp

    fun isActive(): Boolean = status == MatchStatus.ACTIVE && !isExpired()

    fun canSchedulePlaydate(): Boolean = isActive() && !isArchived

    fun getOtherUserId(currentUserId: String): String =
        if (user1Id == currentUserId) user2Id else user1Id

    fun getOtherDogId(currentDogId: String): String =
        if (dog1Id == currentDogId) dog2Id else dog1Id
}


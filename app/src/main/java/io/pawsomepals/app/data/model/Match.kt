package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.firebase.database.IgnoreExtraProperties
import io.pawsomepals.app.data.converters.Converters
import io.pawsomepals.app.data.repository.DogProfileRepository
import kotlin.math.roundToInt

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
    val matchType: MatchType = MatchType.NORMAL,
    val locationDistance: Double? = null,
    val initiatorDogId: String = "",

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

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "user1Id" to user1Id,
            "user2Id" to user2Id,
            "dog1Id" to dog1Id,
            "dog2Id" to dog2Id,
            "compatibilityScore" to compatibilityScore,
            "status" to status.name,
            "matchReasons" to matchReasons,
            "locationDistance" to locationDistance,
            "matchType" to matchType.name,
            "initiatorDogId" to initiatorDogId,
            "timestamp" to timestamp,
            "lastInteractionTimestamp" to lastInteractionTimestamp,
            "expiryTimestamp" to expiryTimestamp,
            "preferredPlaydateLocation" to preferredPlaydateLocation,
            "preferredPlaydateTime" to preferredPlaydateTime,
            "isArchived" to isArchived,
            "isHidden" to isHidden,
            "hasUnreadMessages" to hasUnreadMessages
        )
    }

    data class MatchWithDetails(
        val match: Match,
        val otherDog: Dog,
        val distanceAway: String
    )

    suspend fun Match.toMatchWithDetails(
        currentDogId: String,
        dogRepository: DogProfileRepository
    ): MatchWithDetails {
        val otherDogId = getOtherDogId(currentDogId)
        val otherDog = dogRepository.getDogById(otherDogId).getOrNull()
            ?: throw IllegalStateException("Dog not found")
        val distance = locationDistance?.let { "${it.roundToInt()} km" } ?: "Unknown"

        return MatchWithDetails(
            match = this,
            otherDog = otherDog,
            distanceAway = distance
        )
    }

    suspend fun getOtherDog(currentDogId: String, dogRepository: DogProfileRepository): Dog {
        val otherDogId = getOtherDogId(currentDogId)
        return dogRepository.getDogById(otherDogId).getOrNull()
            ?: throw IllegalStateException("Dog not found")
    }
}

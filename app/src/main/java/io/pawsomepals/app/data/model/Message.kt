package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "messages")
@IgnoreExtraProperties
data class Message(
    @PrimaryKey val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String = "",  // Add this field for username display

    // Enhanced fields
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val metadata: Map<String, Any>? = null,  // For special message types
    val replyToMessageId: String? = null,     // For message replies
    val isFromCurrentUser: Boolean = false  // Add this

) {

    // No-argument constructor required by Firebase
    constructor() : this("", "", "", "")

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    fun isPlaydateRelated(): Boolean =
        type == MessageType.PLAYDATE_SUGGESTION ||
                metadata?.get("isPlaydateRelated") == true

    fun getLocationData(): LocationData? {
        if (type != MessageType.LOCATION_SHARE) return null
        return metadata?.let {
            LocationData(
                latitude = it["latitude"] as? Double ?: return null,
                longitude = it["longitude"] as? Double ?: return null,
                locationName = it["locationName"] as? String ?: ""
            )
        }
    }
    data class PlaydateMetadata(
        val status: PlaydateStatus,
        val suggestedTime: Long? = null,
        val suggestedLocation: LocationData? = null,
        val confirmedBy: List<String> = emptyList(),
        val lastUpdated: Long = System.currentTimeMillis()
    )

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "chatId" to chatId,
        "senderId" to senderId,
        "content" to content,
        "senderName" to senderName,  // Add this to the map

        "timestamp" to timestamp,
        "type" to type.name,
        "status" to status.name,
        "metadata" to metadata,
        "replyToMessageId" to replyToMessageId
    )
}

enum class MessageType {
    TEXT,
    LOCATION,
    IMAGE,
    SYSTEM,
    PLAYDATE_SUGGESTION,
    PLAYDATE_CONFIRMATION,
    LOCATION_SHARE,
    QUICK_REPLY,
    DOG_PROFILE_SHARE,
    PLAYDATE_MOOD,
    PLAYDATE_UPDATE,
    COMPATIBILITY_PROMPT;

    fun getIcon(): String = when (this) {
        TEXT -> "💭"
        LOCATION -> "LOC"
        IMAGE -> "🖼️"
        SYSTEM -> "ℹ️"
        PLAYDATE_SUGGESTION -> "🎯"
        PLAYDATE_CONFIRMATION -> "✅"
        LOCATION_SHARE -> "📍"
        QUICK_REPLY -> "⚡"
        DOG_PROFILE_SHARE -> "🐕"
        PLAYDATE_MOOD -> "😊"
        PLAYDATE_UPDATE -> "📝"
        COMPATIBILITY_PROMPT -> "🤝"
    }
}
enum class MessageStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED
}

// Helper data classes
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val locationName: String
)

data class PlaydateSuggestion(
    val dateTime: Long,
    val location: LocationData,
    val suggestedBy: String
)

// Extension function to help with Room conversion
fun Map<String, Any>.toPlaydateSuggestion(): PlaydateSuggestion? {
    return try {
        PlaydateSuggestion(
            dateTime = this["dateTime"] as Long,
            location = LocationData(
                latitude = (this["latitude"] as? Double) ?: 0.0,
                longitude = (this["longitude"] as? Double) ?: 0.0,
                locationName = (this["locationName"] as? String) ?: ""
            ),
            suggestedBy = this["suggestedBy"] as String
        )
    } catch (e: Exception) {
        null
    }
}
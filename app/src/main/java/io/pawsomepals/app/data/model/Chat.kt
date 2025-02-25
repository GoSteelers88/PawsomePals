package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "chats")
@IgnoreExtraProperties
data class Chat(
    @PrimaryKey val id: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val lastMessageTimestamp: Long = 0,
    val lastMessagePreview: String = "",
    // New fields
    val matchId: String = "",  // Reference to the match that created this chat
    val dog1Id: String = "",   // For quick access to dog profiles
    val dog2Id: String = "",
    val participants: List<String> = listOf(),  // Combines user1Id and user2Id for easier queries
    val lastMessageType: MessageType = MessageType.TEXT,
    val hasUnreadMessages: Boolean = false,
    val playdateStatus: PlaydateStatus? = null,
    val created: Long = System.currentTimeMillis()
) {
    fun getOtherUserId(currentUserId: String): String {
        return if (currentUserId == user1Id) user2Id else user1Id
    }

    fun getOtherDogId(currentDogId: String): String {
        return if (currentDogId == dog1Id) dog2Id else dog1Id
    }
    enum class PlaydateStatus {
        NONE,               // No playdate discussed yet
        SCHEDULING,         // Currently scheduling
        DATE_SUGGESTED,     // One party suggested a date/time
        LOCATION_SUGGESTED, // One party suggested a location
        PENDING_CONFIRMATION, // Waiting for final confirmation
        CONFIRMED,          // Both parties confirmed
        CANCELLED           // Playdate was cancelled
    }

    data class ChatUIState(
        val id: String,
        val otherDogPhotoUrl: String?,
        val otherDogName: String,
        val lastMessage: String?,
        val timestamp: Long,
        val isNewMatch: Boolean,
        val hasUnreadMessages: Boolean,
        val isPendingPlaydate: Boolean
    )
    data class ChatWithDetails(
        val chat: Chat,
        val otherDogPhotoUrl: String? = null,
        val otherDogName: String = "Unknown Dog",
        val isNewMatch: Boolean = false,
        val pendingPlaydate: Boolean = false
    )
    val formattedLastMessageTime: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(lastMessageTimestamp))
        }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "user1Id" to user1Id,
        "user2Id" to user2Id,
        "lastMessageTimestamp" to lastMessageTimestamp,
        "lastMessagePreview" to lastMessagePreview,
        "matchId" to matchId,
        "dog1Id" to dog1Id,
        "dog2Id" to dog2Id,
        "participants" to participants,
        "lastMessageType" to lastMessageType.name,
        "hasUnreadMessages" to hasUnreadMessages,
        "playdateStatus" to playdateStatus?.name,
        "created" to created
    )
}
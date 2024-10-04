package com.example.pawsomepals.data.model

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
    val lastMessagePreview: String = ""
) {
    fun getOtherUserId(currentUserId: String): String {
        return if (currentUserId == user1Id) user2Id else user1Id
    }

    val formattedLastMessageTime: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(lastMessageTimestamp))
        }
}
package com.example.pawsomepals.data.model

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
    val isRead: Boolean = false
) {
    // No-argument constructor required by Firebase
    constructor() : this("", "", "", "")

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
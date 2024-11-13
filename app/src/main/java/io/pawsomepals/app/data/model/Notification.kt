package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: NotificationType = NotificationType.OTHER,
    val read: Boolean = false,
    val metadata: Map<String, Any>? = null,
    val actionUrl: String? = null
)

enum class NotificationType {
    MATCH {
        override fun getIcon() = "💕"
        override fun getDescription() = "New match notification"
    },
    MESSAGE {
        override fun getIcon() = "💬"
        override fun getDescription() = "New message notification"
    },
    PLAYDATE {
        override fun getIcon() = "🎯"
        override fun getDescription() = "Playdate related notification"
    },
    ACHIEVEMENT {
        override fun getIcon() = "🏆"
        override fun getDescription() = "Achievement notification"
    },
    SAFETY {
        override fun getIcon() = "🛡️"
        override fun getDescription() = "Safety related notification"
    },
    SYSTEM {
        override fun getIcon() = "⚙️"
        override fun getDescription() = "System notification"
    },
    OTHER {
        override fun getIcon() = "📢"
        override fun getDescription() = "Other notification"
    };

    abstract fun getIcon(): String
    abstract fun getDescription(): String
}
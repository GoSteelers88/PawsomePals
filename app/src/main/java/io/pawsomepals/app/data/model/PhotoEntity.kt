package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["ownerId", "isUserPhoto", "index"], unique = true)
    ]
)
data class PhotoEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val isUserPhoto: Boolean,
    val ownerId: String,
    val index: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
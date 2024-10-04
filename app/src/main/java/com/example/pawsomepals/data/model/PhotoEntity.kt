package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String, // This could be a UUID or URL
    val url: String,
    val isUserPhoto: Boolean,
    val ownerId: String, // This could be userId or dogId
    val uploadDate: Long = System.currentTimeMillis(),
    val description: String? = null
)
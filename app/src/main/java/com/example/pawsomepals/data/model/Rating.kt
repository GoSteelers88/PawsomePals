package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratings")
data class Rating(
    @PrimaryKey val id: String, // This could be a UUID
    val userId: String, // ID of the user being rated
    val raterId: String, // ID of the user giving the rating
    val score: Float, // Assuming a scale, e.g., 1.0 to 5.0
    val comment: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val playdateId: String? = null // Optional: link to specific playdate
)
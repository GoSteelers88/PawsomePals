package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey val id: String,
    val userId: String,
    val question: String,
    val answer: String,
    val timestamp: Long,
    val options: List<String>? = null
)
package com.example.pawsomepals.data.model


import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "questionnaire_responses")
data class QuestionnaireResponse(
    @PrimaryKey val userId: String,
    val responses: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)


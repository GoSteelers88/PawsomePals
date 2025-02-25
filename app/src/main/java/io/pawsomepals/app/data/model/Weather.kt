package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather")
data class WeatherEntity(
    @PrimaryKey val id: String = "current_weather", // Single weather record
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val condition: String,
    val icon: String,
    val timestamp: Long = System.currentTimeMillis()
)
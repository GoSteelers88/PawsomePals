package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timeslots")
data class Timeslot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: String,
    val endTime: String,
    val dayOfWeek: Int // 1 for Monday, 7 for Sunday
)
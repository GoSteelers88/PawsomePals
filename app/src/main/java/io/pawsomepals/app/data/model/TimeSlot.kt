package io.pawsomepals.app.data.model

import java.time.DayOfWeek
import java.time.LocalTime

data class TimeSlot(
    val id: String,
    val userId: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val date: Long? = null,
    val isAvailable: Boolean = true,
    val existingPlaydate: String? = null
)

package io.pawsomepals.app.data.model

import java.time.DayOfWeek
import java.util.UUID

data class TimeSlotUi(
    val id: String = UUID.randomUUID().toString(),
    val dayOfWeek: DayOfWeek,
    val startTime: String,  // Format: "HH:mm"
    val endTime: String,    // Format: "HH:mm"
    val date: Long? = null,
    val isAvailable: Boolean = true,
    val existingPlaydate: PlaydateWithDetails? = null
)
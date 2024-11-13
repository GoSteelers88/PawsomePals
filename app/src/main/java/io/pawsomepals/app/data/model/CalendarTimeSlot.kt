package io.pawsomepals.app.data.model

data class CalendarTimeSlot(
    val startTime: Long,
    val endTime: Long,
    val isAvailable: Boolean
)
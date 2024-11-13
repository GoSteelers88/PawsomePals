
package io.pawsomepals.app.data.repository

import io.pawsomepals.app.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow

interface ITimeSlotRepository {
    suspend fun getTimeSlotsForDay(userId: String, dayOfWeek: Int): List<TimeSlot>
    fun getAllTimeSlots(userId: String): Flow<List<TimeSlot>>
    suspend fun updateTimeSlots(timeSlots: List<TimeSlot>)
    suspend fun insertTimeSlot(timeSlot: TimeSlot)
    suspend fun deleteTimeSlot(timeSlotId: Int)
    suspend fun deleteTimeSlot(timeSlotId: String)
    suspend fun deleteAllUserTimeSlots(userId: String)
}
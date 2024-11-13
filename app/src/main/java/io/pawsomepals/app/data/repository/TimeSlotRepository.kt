package io.pawsomepals.app.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import io.pawsomepals.app.data.dao.TimeSlotDao
import io.pawsomepals.app.data.model.TimeSlot
import io.pawsomepals.app.data.model.toEntity
import io.pawsomepals.app.data.model.toTimeSlot
import io.pawsomepals.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeSlotRepository @Inject constructor(
    private val timeSlotDao: TimeSlotDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ITimeSlotRepository {  // Implement the interface

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getTimeSlotsForDay(userId: String, dayOfWeek: Int): List<TimeSlot> {
        return withContext(dispatcher) {
            timeSlotDao.getTimeSlotsForDay(dayOfWeek).map { it.toTimeSlot(userId) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun getAllTimeSlots(userId: String): Flow<List<TimeSlot>> {
        return timeSlotDao.getAllTimeSlots().map { entities ->
            entities.map { it.toTimeSlot(userId) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun updateTimeSlots(timeSlots: List<TimeSlot>) {
        withContext(dispatcher) {
            timeSlotDao.upsertAll(timeSlots.map { it.toEntity() })
        }
    }

    override suspend fun insertTimeSlot(timeSlot: TimeSlot) {
        withContext(dispatcher) {
            timeSlotDao.insert(timeSlot.toEntity())
        }
    }

    override suspend fun deleteTimeSlot(timeSlotId: Int) {
        withContext(dispatcher) {
            timeSlotDao.deleteTimeSlot(timeSlotId)
        }
    }

    override suspend fun deleteTimeSlot(timeSlotId: String) {
        withContext(dispatcher) {
            timeSlotDao.deleteTimeSlot(timeSlotId.toInt())
        }
    }

    override suspend fun deleteAllUserTimeSlots(userId: String) {
        withContext(dispatcher) {
            timeSlotDao.deleteAllUserTimeSlots(userId)
        }
    }
}
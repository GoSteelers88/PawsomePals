// TimeSlotDao.kt
package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pawsomepals.app.data.model.TimeslotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {
    @Query("SELECT * FROM timeslots WHERE dayOfWeek = :dayOfWeek")
    suspend fun getTimeSlotsForDay(dayOfWeek: Int): List<TimeslotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timeSlot: TimeslotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(timeSlots: List<TimeslotEntity>)

    @Delete
    suspend fun delete(timeSlot: TimeslotEntity)

    @Query("DELETE FROM timeslots WHERE id = :timeSlotId")
    suspend fun deleteTimeSlot(timeSlotId: Int)

    @Query("SELECT * FROM timeslots")
    fun getAllTimeSlots(): Flow<List<TimeslotEntity>>

    @Query("SELECT * FROM timeslots WHERE userId = :userId")
    fun getTimeSlotsByUser(userId: String): Flow<List<TimeslotEntity>>

    @Query("DELETE FROM timeslots WHERE userId = :userId")
    suspend fun deleteAllUserTimeSlots(userId: String)
}
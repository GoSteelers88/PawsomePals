package com.example.pawsomepals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pawsomepals.data.model.PlaydateRequest
import com.example.pawsomepals.data.model.Timeslot
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaydateDao {
    @Query("SELECT * FROM playdate_requests")
    fun getAllPlaydateRequests(): Flow<List<PlaydateRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaydateRequest(playdateRequest: PlaydateRequest): Long

    @Update
    suspend fun updatePlaydateRequest(playdateRequest: PlaydateRequest)

    @Query("SELECT * FROM playdate_requests WHERE :startTimestamp <= suggestedTimeslots AND suggestedTimeslots <= :endTimestamp")
    suspend fun getPlaydatesForDateRange(startTimestamp: Long, endTimestamp: Long): List<PlaydateRequest>

    @Query("SELECT * FROM timeslots")
    fun getAllTimeslots(): Flow<List<Timeslot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeslot(timeslot: Timeslot)
}
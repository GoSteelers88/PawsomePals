package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateCalendarInfo
import io.pawsomepals.app.data.model.PlaydateRequest
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.TimeslotEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface PlaydateDao {
    // ====== Playdate Basic Operations ======
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaydate(playdate: Playdate)



    @Update
    suspend fun updatePlaydate(playdate: Playdate)

    @Query("DELETE FROM playdates WHERE id = :playdateId")
    suspend fun deletePlaydate(playdateId: String)

    @Query("SELECT * FROM playdate_requests WHERE id = :requestId")
    suspend fun getPlaydateRequestById(requestId: String): PlaydateRequest?



    @Query("""
        SELECT COUNT(*) FROM playdates 
        WHERE scheduledTime >= :weekStart 
        AND scheduledTime <= :weekEnd
        AND (dog1Id = :userId OR dog2Id = :userId)
    """)
    suspend fun getPlaydatesCountForWeek(
        userId: String,
        weekStart: Long,
        weekEnd: Long
    ): Int

    @Query("""
        SELECT SUM((julianday(endTime) - julianday(startTime)) * 24) 
        FROM timeslots 
        WHERE dayOfWeek = :dayOfWeek
    """)
    suspend fun getAvailableHoursForDay(dayOfWeek: Int): Float?

    @Query("""
        SELECT COUNT(*) FROM playdates 
        WHERE status = 'COMPLETED'
        AND (dog1Id = :userId OR dog2Id = :userId)
    """)
    suspend fun getCompletedPlaydatesCount(userId: String): Int

    @Query("SELECT * FROM playdates WHERE id = :playdateId")
    suspend fun getPlaydateById(playdateId: String): Playdate?


    @Query("SELECT * FROM playdates")
    fun getAllPlaydates(): Flow<List<Playdate>>

    // ====== PlaydateRequest Basic Operations ======
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaydateRequest(playdateRequest: PlaydateRequest): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarInfo(calendarInfo: PlaydateCalendarInfo)

    @Update
    suspend fun updatePlaydateRequest(playdateRequest: PlaydateRequest)

    @Query("DELETE FROM playdate_requests WHERE id = :requestId")
    suspend fun deletePlaydateRequest(requestId: String)

    @Query("SELECT * FROM playdate_requests WHERE playdateId = :playdateId")
    suspend fun getRequestsByPlaydateId(playdateId: String): List<PlaydateRequest>

    @Query("SELECT * FROM playdate_requests")
    fun getAllPlaydateRequests(): Flow<List<PlaydateRequest>>

    // ====== Timeslot Operations ======
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeslot(timeslotEntity: TimeslotEntity)

    @Query("SELECT * FROM timeslots")
    fun getAllTimeslots(): Flow<List<TimeslotEntity>>

    @Query("DELETE FROM timeslots WHERE playdateId = :playdateId")
    suspend fun deleteTimeslotsByPlaydateId(playdateId: String)

    @Update
    suspend fun updateTimeslot(timeslotEntity: TimeslotEntity)

    @Delete
    suspend fun deleteTimeslot(timeslotEntity: TimeslotEntity)

    // ====== Complex Queries ======
    @Query("""
        SELECT * FROM playdates 
        WHERE scheduledTime BETWEEN :startTimestamp AND :endTimestamp
        ORDER BY scheduledTime ASC
    """)
    suspend fun getPlaydatesForDateRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): List<Playdate>

    @Query("""
        SELECT COUNT(*) FROM playdates
        WHERE status = :status
        AND (dog1Id = :dogId OR dog2Id = :dogId)
    """)
    suspend fun getPlaydateCountByStatus(
        status: PlaydateStatus,
        dogId: String
    ): Int

    // ====== Status Operations ======
    @Query("""
        UPDATE playdates 
        SET status = :newStatus,
            updatedAt = :timestamp
        WHERE id = :playdateId
    """)
    suspend fun updatePlaydateStatus(
        playdateId: String,
        newStatus: PlaydateStatus,
        timestamp: Long = System.currentTimeMillis()
    )


    @Transaction
    @Query("""
        SELECT * FROM playdates
        WHERE status = :status
        ORDER BY scheduledTime DESC
    """)
    fun getPlaydatesByStatus(status: PlaydateStatus): Flow<List<PlaydateWithRequests>>


    // Add method to get dog ID for user
    @Query("""
        SELECT id FROM dogs
        WHERE ownerId = :userId
        LIMIT 1
    """)
    suspend fun getDogIdForUser(userId: String): String?

    // Optional: Add a method to get all dog IDs for a user
    @Query("""
        SELECT id FROM dogs
        WHERE ownerId = :userId
    """)
    suspend fun getDogIdsForUser(userId: String): List<String>

    @Transaction
    @Query("""
        SELECT * FROM playdates
        WHERE status = :status
        AND (dog1Id = :dogId OR dog2Id = :dogId)
        ORDER BY scheduledTime DESC
    """)
    fun getPlaydatesByStatus(
        status: PlaydateStatus,
        dogId: String
    ): Flow<List<PlaydateWithRequests>>

    // ====== Relationship Queries ======
    @Transaction
    @Query("SELECT * FROM playdates WHERE id = :playdateId")
    suspend fun getPlaydateWithRequests(playdateId: String): PlaydateWithRequests?

    @Query("""
        SELECT * FROM playdate_requests 
        WHERE playdateId = :playdateId 
        AND (requesterId = :userId OR receiverId = :userId)
    """)
    suspend fun getPlaydateRequestForUser(
        playdateId: String,
        userId: String
    ): PlaydateRequest?

    // ====== Safe Transaction Operations ======
    @Transaction
    suspend fun createPlaydateWithRequest(playdate: Playdate, request: PlaydateRequest) {
        insertPlaydate(playdate)
        insertPlaydateRequest(request.copy(playdateId = playdate.id))
    }

    @Transaction
    suspend fun safeInsertPlaydateRequest(request: PlaydateRequest) {
        getPlaydateById(request.playdateId)?.let {
            insertPlaydateRequest(request)
        } ?: throw IllegalStateException("Cannot insert request: Playdate ${request.playdateId} does not exist")
    }

    @Transaction
    suspend fun deletePlaydateWithRelated(playdateId: String) {
        deleteTimeslotsByPlaydateId(playdateId)
        getRequestsByPlaydateId(playdateId).forEach { request ->
            deletePlaydateRequest(request.id)
        }
        deletePlaydate(playdateId)
    }

    @Transaction
    suspend fun updatePlaydateWithRequests(
        playdate: Playdate,
        requests: List<PlaydateRequest>
    ) {
        updatePlaydate(playdate)
        requests.forEach { request ->
            require(request.playdateId == playdate.id) {
                "Request playdateId must match playdate.id"
            }
            insertPlaydateRequest(request)
        }
    }
}

// Relationship class for combined queries
data class PlaydateWithRequests(
    @Embedded
    val playdate: Playdate,

    @Relation(
        parentColumn = "id",
        entityColumn = "playdateId"
    )
    val requests: List<PlaydateRequest>
)
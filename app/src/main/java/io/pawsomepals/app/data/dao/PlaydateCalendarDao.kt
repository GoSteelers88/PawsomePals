package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.pawsomepals.app.data.model.CalendarSyncStatus
import io.pawsomepals.app.data.model.PlaydateCalendarInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaydateCalendarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calendarInfo: PlaydateCalendarInfo)

    @Query("SELECT * FROM playdate_calendar_sync WHERE playdateId = :playdateId")
    suspend fun getCalendarInfoForPlaydate(playdateId: String): PlaydateCalendarInfo?

    @Query("""
    UPDATE playdate_calendar_sync 
    SET calendarEventId = :calendarEventId,
        lastSyncTimestamp = :timestamp,
        syncStatus = :status,
        failureCount = :failureCount,
        lastError = CASE 
            WHEN :status = 'ERROR' THEN :error 
            ELSE NULL 
        END
    WHERE playdateId = :playdateId
    """)
    suspend fun updateCalendarInfo(
        playdateId: String,
        calendarEventId: String?,
        timestamp: Long = System.currentTimeMillis(),
        status: CalendarSyncStatus,
        failureCount: Int = 0,
        error: String? = null
    )

    @Query("""
        SELECT * FROM playdate_calendar_sync 
        WHERE syncStatus = :status 
        AND lastSyncTimestamp < :timestamp
        AND (failureCount < 3 OR lastSyncTimestamp < :retryAfter)
        """)
    suspend fun getPendingSyncs(
        status: CalendarSyncStatus = CalendarSyncStatus.PENDING,
        timestamp: Long = System.currentTimeMillis(),
        retryAfter: Long = System.currentTimeMillis() - (6 * 60 * 60 * 1000) // 6 hours
    ): List<PlaydateCalendarInfo>

    @Delete
    suspend fun delete(calendarInfo: PlaydateCalendarInfo)

    @Query("DELETE FROM playdate_calendar_sync WHERE playdateId = :playdateId")
    suspend fun deleteByPlaydateId(playdateId: String)

    @Query("SELECT * FROM playdate_calendar_sync")
    fun getAllCalendarInfo(): Flow<List<PlaydateCalendarInfo>>

    @Transaction
    suspend fun syncCalendarEvent(calendarInfo: PlaydateCalendarInfo) {
        val existing = getCalendarInfoForPlaydate(calendarInfo.playdateId)
        if (existing != null) {
            updateCalendarInfo(
                playdateId = calendarInfo.playdateId,
                calendarEventId = calendarInfo.calendarEventId,
                timestamp = System.currentTimeMillis(),
                status = calendarInfo.syncStatus,
                error = calendarInfo.lastError
            )
        } else {
            insert(calendarInfo)
        }
    }
}
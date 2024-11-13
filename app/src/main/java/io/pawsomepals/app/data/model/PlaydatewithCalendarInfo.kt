package io.pawsomepals.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playdate_calendar_sync",
    foreignKeys = [
        ForeignKey(
            entity = Playdate::class,           // Changed to reference Playdate
            parentColumns = ["id"],             // References Playdate's primary key
            childColumns = ["playdateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playdateId")]
)
data class PlaydateCalendarInfo(
    @PrimaryKey
    @ColumnInfo(name = "playdateId", defaultValue = "''")
    val playdateId: String = "",

    @ColumnInfo(name = "calendarEventId", defaultValue = "NULL")
    val calendarEventId: String? = null,

    @ColumnInfo(name = "lastSyncTimestamp", defaultValue = "0")
    val lastSyncTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "syncStatus", defaultValue = "'PENDING'")
    val syncStatus: CalendarSyncStatus = CalendarSyncStatus.PENDING,

    @ColumnInfo(name = "failureCount", defaultValue = "0")
    val failureCount: Int = 0,

    @ColumnInfo(name = "lastError", defaultValue = "NULL")
    val lastError: String? = null
)

enum class CalendarSyncStatus {
    PENDING,
    SYNCED,
    ERROR,
    CANCELLED,
    FAILED,  // Added
    DELETED  // Added
}
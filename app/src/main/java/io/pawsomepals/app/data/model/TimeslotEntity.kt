package io.pawsomepals.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timeslots")
data class TimeslotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(defaultValue = "''")
    val userId: String = "",    // Added userId field

    @ColumnInfo(defaultValue = "''")
    val startTime: String,      // Format: "HH:mm"

    @ColumnInfo(defaultValue = "''")
    val endTime: String,        // Format: "HH:mm"

    @ColumnInfo(defaultValue = "0")
    val dayOfWeek: Int,         // 1-7 for Monday-Sunday

    @ColumnInfo(defaultValue = "0")
    val date: Long = 0,         // epochDay

    @ColumnInfo(defaultValue = "1")
    val isAvailable: Boolean = true,

    @ColumnInfo(defaultValue = "''")
    val playdateId: String = ""
)
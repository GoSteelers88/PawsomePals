package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

@Entity(tableName = "user_availability")
data class UserAvailability(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val dayOfWeek: DayOfWeek,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val isRecurring: Boolean = true,
    val specificDate: Long? = null // Used when isRecurring is false
)
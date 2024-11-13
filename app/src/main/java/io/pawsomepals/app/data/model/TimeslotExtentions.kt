package io.pawsomepals.app.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import io.pawsomepals.app.utils.TimeFormatUtils
import java.time.DayOfWeek

@RequiresApi(Build.VERSION_CODES.O)
fun TimeslotEntity.toTimeSlot(userId: String): TimeSlot {
    return TimeSlot(
        id = id.toString(),
        userId = userId,
        dayOfWeek = DayOfWeek.of(dayOfWeek),
        startTime = TimeFormatUtils.parseTime(startTime),
        endTime = TimeFormatUtils.parseTime(endTime),
        date = date,
        isAvailable = isAvailable,
        existingPlaydate = playdateId.takeIf { it.isNotEmpty() }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
fun TimeSlot.toEntity(): TimeslotEntity {
    return TimeslotEntity(
        userId = userId,
        startTime = TimeFormatUtils.formatLocalTime(startTime),
        endTime = TimeFormatUtils.formatLocalTime(endTime),
        dayOfWeek = dayOfWeek.value,
        date = date ?: 0L,
        isAvailable = isAvailable,
        playdateId = existingPlaydate ?: ""
    )
}

@RequiresApi(Build.VERSION_CODES.O)
fun TimeSlot.toTimeSlotUi(): TimeSlotUi {
    return TimeSlotUi(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = TimeFormatUtils.formatLocalTime(startTime),
        endTime = TimeFormatUtils.formatLocalTime(endTime),
        date = date,
        isAvailable = isAvailable,
        existingPlaydate = null
    )
}

@RequiresApi(Build.VERSION_CODES.O)
fun TimeSlotUi.toDomainModel(userId: String): TimeSlot {
    return TimeSlot(
        id = id,
        userId = userId,
        dayOfWeek = dayOfWeek,
        startTime = TimeFormatUtils.parseTime(startTime),
        endTime = TimeFormatUtils.parseTime(endTime),
        date = date,
        isAvailable = isAvailable,
        existingPlaydate = existingPlaydate?.playdate?.id
    )
}
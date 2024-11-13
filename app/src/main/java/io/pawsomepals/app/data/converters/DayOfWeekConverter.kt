package io.pawsomepals.app.data.converters

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.TypeConverter
import java.time.DayOfWeek

class DayOfWeekConverter {
    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): Int {
        return value?.value ?: 0
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    fun toDayOfWeek(value: Int): DayOfWeek? {
        return if (value > 0) DayOfWeek.of(value) else null
    }
}
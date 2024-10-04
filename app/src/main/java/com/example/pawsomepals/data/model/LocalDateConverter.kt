package com.example.pawsomepals.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.TypeConverter
import java.time.LocalDate

object LocalDateConverter {
    @TypeConverter
    @JvmStatic
    fun fromTimestamp(value: Long?): LocalDate? {
        return value?.let { LocalDate.ofEpochDay(it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    @JvmStatic
    fun dateToTimestamp(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }
}
package io.pawsomepals.app.utils

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimeFormatUtils {
    @RequiresApi(Build.VERSION_CODES.O)
    val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatLocalTime(time: LocalTime): String {
        return time.format(timeFormatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun parseTime(timeString: String): LocalTime {
        return LocalTime.parse(timeString, timeFormatter)
    }
}
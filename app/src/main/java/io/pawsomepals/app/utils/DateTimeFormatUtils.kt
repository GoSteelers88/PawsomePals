package io.pawsomepals.app.utils

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DateTimeFormatUtils {
    @RequiresApi(Build.VERSION_CODES.O)
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    @RequiresApi(Build.VERSION_CODES.O)
    val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatDate(epochMillis: Long): String {
        return LocalDate.ofEpochDay(epochMillis / (24 * 60 * 60 * 1000))
            .format(dateFormatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatTime(epochMillis: Long): String {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneOffset.UTC
        ).format(timeFormatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatDateTime(epochMillis: Long): String {
        val date = formatDate(epochMillis)
        val time = formatTime(epochMillis)
        return "$date at $time"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun parseDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr, dateFormatter)
    }
}
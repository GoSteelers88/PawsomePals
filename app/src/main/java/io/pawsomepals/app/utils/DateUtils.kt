package io.pawsomepals.app.utils

import android.os.Build
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {
    @RequiresApi(Build.VERSION_CODES.O)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")
    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    @RequiresApi(Build.VERSION_CODES.O)
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
    @RequiresApi(Build.VERSION_CODES.O)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM dd")

    private val relativeDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val relativeTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatDateTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return dateTime.format(dateTimeFormatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatDate(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return date.format(dateFormatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val time = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return time.format(timeFormatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatShortDate(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return date.format(shortDateFormatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getRelativeTimeSpan(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> formatDate(timestamp)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatPlaydateTime(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val now = Calendar.getInstance()

        return when {
            isSameDay(calendar, now) -> "Today at ${formatTime(timestamp)}"
            isNextDay(calendar, now) -> "Tomorrow at ${formatTime(timestamp)}"
            isPreviousDay(calendar, now) -> "Yesterday at ${formatTime(timestamp)}"
            else -> formatDateTime(timestamp)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun LocalDate.toEpochMilli(): Long {
        return this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isNextDay(cal1: Calendar, cal2: Calendar): Boolean {
        val nextDay = cal2.clone() as Calendar
        nextDay.add(Calendar.DAY_OF_YEAR, 1)
        return isSameDay(cal1, nextDay)
    }

    private fun isPreviousDay(cal1: Calendar, cal2: Calendar): Boolean {
        val previousDay = cal2.clone() as Calendar
        previousDay.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(cal1, previousDay)
    }

    // Helper function to check if a date is today
    fun isToday(timestamp: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val now = Calendar.getInstance()
        return isSameDay(calendar, now)
    }

    // Helper function to get start of day timestamp
    fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // Helper function to get end of day timestamp
    fun getEndOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
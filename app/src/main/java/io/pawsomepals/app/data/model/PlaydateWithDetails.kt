package io.pawsomepals.app.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import io.pawsomepals.app.utils.DateTimeFormatUtils

data class PlaydateWithDetails(
    val playdate: Playdate,
    val otherDog: Dog,
    val location: DogFriendlyLocation,
    val status: PlaydateStatus
) {
    val formattedDate: String
        @RequiresApi(Build.VERSION_CODES.O)
        get() = DateTimeFormatUtils.formatDate(playdate.scheduledTime)

    val formattedTime: String
        @RequiresApi(Build.VERSION_CODES.O)
        get() = DateTimeFormatUtils.formatTime(playdate.scheduledTime)

    val formattedDateTime: String
        @RequiresApi(Build.VERSION_CODES.O)
        get() = DateTimeFormatUtils.formatDateTime(playdate.scheduledTime)
}

enum class PlaydateTab {
    UPCOMING,
    CALENDAR,
    AVAILABLE,
    LOCATIONS,
    HISTORY
}

data class ReminderSetting(
    val id: String,
    val minutes: Int,
    val type: ReminderType
)

enum class ReminderType {
    NOTIFICATION,
    EMAIL
}

// Remove the standalone dateFormatter since it's now in DateTimeFormatUtils
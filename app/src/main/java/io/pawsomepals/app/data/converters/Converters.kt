package io.pawsomepals.app.data.converters

import androidx.room.TypeConverter
import io.pawsomepals.app.data.model.Achievement
import io.pawsomepals.app.data.model.CalendarSyncStatus
import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.MessageStatus
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.RequestStatus
import io.pawsomepals.app.data.model.SwipeDirection
import io.pawsomepals.app.data.model.SwipeLocation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pawsomepals.app.data.model.PlaydateWithDetails
import java.time.DayOfWeek


class Converters {
    private val gson = Gson()

    // Basic List/Map Converters
    @TypeConverter
    fun fromStringToList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }


    class DayOfWeekConverter {
        @TypeConverter
        fun toDayOfWeek(value: Int): DayOfWeek? {
            return when (value) {
                1 -> DayOfWeek.MONDAY
                2 -> DayOfWeek.TUESDAY
                3 -> DayOfWeek.WEDNESDAY
                4 -> DayOfWeek.THURSDAY
                5 -> DayOfWeek.FRIDAY
                6 -> DayOfWeek.SATURDAY
                7 -> DayOfWeek.SUNDAY
                else -> null
            }
        }

        @TypeConverter
        fun fromDayOfWeek(day: DayOfWeek?): Int {
            return day?.value ?: 0
        }
    }

    @TypeConverter
    fun fromListToString(list: List<String>?): String? {
        if (list == null) return null
        return gson.toJson(list)
    }



@TypeConverter
    fun fromStringToMap(value: String?): Map<String, String>? {
        if (value == null) return null
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, mapType)
    }

    @TypeConverter
    fun fromMapToString(map: Map<String, String>?): String? {
        if (map == null) return null
        return gson.toJson(map)
    }
    // MatchType Converter
    @TypeConverter
    fun fromMatchType(type: MatchType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toMatchType(value: String?): MatchType? {
        if (value == null) return null
        return try {
            MatchType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MatchType.NORMAL
        }
    }

    // SwipeLocation Converters
    @TypeConverter
    fun fromSwipeLocation(location: SwipeLocation?): String? {
        if (location == null) return null
        return "${location.latitude},${location.longitude}"
    }

    @TypeConverter
    fun toSwipeLocation(value: String?): SwipeLocation? {
        if (value == null) return null
        return try {
            val (lat, lon) = value.split(",").map { it.toDouble() }
            SwipeLocation(lat, lon)
        } catch (e: Exception) {
            null
        }
    }

    // MatchReason Converters
    @TypeConverter
    fun fromMatchReasonList(reasons: List<MatchReason>?): String? {
        if (reasons == null) return null
        return gson.toJson(reasons)
    }

    @TypeConverter
    fun toMatchReasonList(value: String?): List<MatchReason>? {
        if (value == null) return null
        val listType = object : TypeToken<List<MatchReason>>() {}.type
        return gson.fromJson(value, listType)
    }

    // Achievement Converters
    @TypeConverter
    fun fromAchievementList(achievements: List<Achievement>?): String? {
        if (achievements == null) return null
        return gson.toJson(achievements)
    }

    @TypeConverter
    fun toAchievementList(value: String?): List<Achievement>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Achievement>>() {}.type
        return gson.fromJson(value, listType)
    }

    // Enum Converters
    @TypeConverter
    fun fromMatchStatus(status: MatchStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toMatchStatus(value: String?): MatchStatus? {
        if (value == null) return null
        return try {
            MatchStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    @TypeConverter
    fun fromSwipeDirection(direction: SwipeDirection?): String? {
        return direction?.name
    }

    @TypeConverter
    fun toSwipeDirection(value: String?): SwipeDirection? {
        if (value == null) return null
        return try {
            SwipeDirection.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SwipeDirection.NONE
        }
    }
    @TypeConverter
    fun fromMessageType(type: MessageType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toMessageType(value: String?): MessageType? {
        if (value == null) return null
        return try {
            MessageType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MessageType.TEXT
        }
    }

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toMessageStatus(value: String?): MessageStatus? {
        if (value == null) return null
        return try {
            MessageStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            MessageStatus.SENT
        }
    }

    @TypeConverter
    fun fromPlaydateStatus(status: PlaydateStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toPlaydateStatus(value: String?): PlaydateStatus? {
        if (value == null) return null
        return try {
            PlaydateStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PlaydateStatus.NONE
        }
    }

    // For metadata Map<String, Any>


    // Double List Converter (for coordinates, scores, etc.)
    @TypeConverter
    fun fromDoubleList(values: List<Double>?): String? {
        if (values == null) return null
        return gson.toJson(values)
    }

    @TypeConverter
    fun toDoubleList(value: String?): List<Double>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Double>>() {}.type
        return gson.fromJson(value, listType)
    }

    // Timestamp Converters
    @TypeConverter
    fun fromTimestamp(value: Long?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toTimestamp(timestamp: String?): Long? {
        return timestamp?.toLongOrNull()
    }
    // Nullable Double Converter (for locationDistance)
    @TypeConverter
    fun fromDouble(value: Double?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toDouble(value: String?): Double? {
        return value?.toDoubleOrNull()
    }

    // Map<String, Any?> Converter (for additional match data)
    @TypeConverter
    fun fromAnyMap(map: Map<String, Any?>?): String? {
        if (map == null) return null
        return gson.toJson(map)
    }

    @TypeConverter
    fun toAnyMap(value: String?): Map<String, Any?>? {
        if (value == null) return null
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(value, mapType)
    }

    // Boolean Converter (for flags)
    @TypeConverter
    fun fromBoolean(value: Boolean?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toBoolean(value: String?): Boolean? {
        return value?.toBooleanStrictOrNull()
    }

    @TypeConverter
    fun fromLongList(list: List<Long>?): String? {
        if (list == null) return null
        return gson.toJson(list)
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromRequestStatus(status: RequestStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toRequestStatus(value: String?): RequestStatus? {
        if (value == null) return null
        return try {
            RequestStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            RequestStatus.PENDING
        }
    }

    @TypeConverter
    fun fromCalendarSyncStatus(status: CalendarSyncStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toCalendarSyncStatus(value: String?): CalendarSyncStatus? {
        if (value == null) return null
        return try {
            CalendarSyncStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            CalendarSyncStatus.PENDING
        }
    }

    class PlaydateWithDetailsConverter {
        private val gson = Gson()

        @TypeConverter
        fun fromPlaydateWithDetails(details: PlaydateWithDetails?): String? {
            if (details == null) return null
            return gson.toJson(details)
        }

        @TypeConverter
        fun toPlaydateWithDetails(value: String?): PlaydateWithDetails? {
            if (value == null) return null
            return try {
                gson.fromJson(value, PlaydateWithDetails::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Add a safety check for Match-specific JSON parsing
    private inline fun <reified T> safeGsonParse(json: String?, defaultValue: T): T {
        return try {
            gson.fromJson(json, T::class.java) ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }
}

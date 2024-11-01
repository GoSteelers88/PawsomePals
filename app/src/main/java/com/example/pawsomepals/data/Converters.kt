package com.example.pawsomepals.data

import androidx.room.TypeConverter
import com.example.pawsomepals.data.model.Achievement
import com.example.pawsomepals.data.model.MatchReason
import com.example.pawsomepals.data.model.MatchStatus
import com.example.pawsomepals.data.model.SwipeDirection
import com.example.pawsomepals.data.model.SwipeLocation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // Basic List/Map Converters
    @TypeConverter
    fun fromStringToList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
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
}
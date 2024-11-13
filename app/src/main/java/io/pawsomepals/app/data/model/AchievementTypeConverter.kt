
package io.pawsomepals.app.data.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AchievementTypeConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromAchievement(achievement: Achievement?): String? {
        return achievement?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toAchievement(value: String?): Achievement? {
        return value?.let {
            gson.fromJson(it, Achievement::class.java)
        }
    }

    @TypeConverter
    fun fromAchievementList(achievements: List<Achievement>?): String? {
        return achievements?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toAchievementList(value: String?): List<Achievement>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Achievement>>() {}.type
        return gson.fromJson(value, listType)
    }
}
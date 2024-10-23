package com.example.pawsomepals.data.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AchievementTypeConverter {
    @TypeConverter
    fun fromString(value: String?): List<Achievement> {
        if (value == null) {
            return emptyList()
        }
        val listType = object : TypeToken<List<Achievement>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<Achievement>): String {
        return Gson().toJson(list)
    }
}
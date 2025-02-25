package io.pawsomepals.app.data.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.pawsomepals.app.data.model.DogFriendlyLocation

class DogFriendlyLocationTypeConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromLocationList(locations: List<DogFriendlyLocation>?): String? {
        return gson.toJson(locations)
    }

    @TypeConverter
    fun toLocationList(value: String?): List<DogFriendlyLocation>? {
        if (value == null) return null
        val listType = object : TypeToken<List<DogFriendlyLocation>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromLocation(location: DogFriendlyLocation?): String? {
        return gson.toJson(location)
    }

    @TypeConverter
    fun toLocation(value: String?): DogFriendlyLocation? {
        if (value == null) return null
        return gson.fromJson(value, DogFriendlyLocation::class.java)
    }
}
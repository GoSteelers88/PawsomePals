package io.pawsomepals.app.data.converters

import androidx.room.TypeConverter
import io.pawsomepals.app.data.model.DogFriendlyLocation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LocationConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromAmenityList(value: List<DogFriendlyLocation.Amenity>?): String? {
        if (value == null) return null
        return gson.toJson(value)
    }

    @TypeConverter
    fun toAmenityList(value: String?): List<DogFriendlyLocation.Amenity>? {
        if (value == null) return null
        val listType = object : TypeToken<List<DogFriendlyLocation.Amenity>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromRestrictionList(value: List<DogFriendlyLocation.Restriction>?): String? {
        if (value == null) return null
        return gson.toJson(value)
    }

    @TypeConverter
    fun toRestrictionList(value: String?): List<DogFriendlyLocation.Restriction>? {
        if (value == null) return null
        val listType = object : TypeToken<List<DogFriendlyLocation.Restriction>>() {}.type
        return gson.fromJson(value, listType)
    }
}
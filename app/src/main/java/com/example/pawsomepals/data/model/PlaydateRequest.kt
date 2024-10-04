package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "playdate_requests")
@TypeConverters(LongListConverter::class)
data class PlaydateRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val requesterId: String,
    val receiverId: String,
    val suggestedTimeslots: List<Long>,
    val status: RequestStatus
)

enum class RequestStatus {
    PENDING, ACCEPTED, DECLINED
}

class LongListConverter {
    @TypeConverter
    fun fromString(value: String): List<Long> {
        val listType = object : TypeToken<List<Long>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<Long>): String {
        return Gson().toJson(list)
    }
}
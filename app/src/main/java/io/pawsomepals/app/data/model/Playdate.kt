package io.pawsomepals.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playdates")
data class Playdate(
    @PrimaryKey
    @ColumnInfo(defaultValue = "''")  // Add explicit default
    val id: String,

    @ColumnInfo(defaultValue = "''")
    val matchId: String = "",  // Add this field



    @ColumnInfo(defaultValue = "''")  // Add explicit default
    val chatId: String = "",

    @ColumnInfo(defaultValue = "''")  // Add explicit default
    val dog1Id: String = "",

    @ColumnInfo(defaultValue = "''")  // Add explicit default
    val dog2Id: String = "",

    @ColumnInfo(defaultValue = "0")  // Add explicit default
    val scheduledTime: Long = 0,

    @ColumnInfo(defaultValue = "''")  // Add explicit default
    val location: String = "",

    @ColumnInfo(defaultValue = "'PENDING'")  // Add explicit default
    val status: PlaydateStatus = PlaydateStatus.PENDING,

    val mood: PlaydateMood? = null,

    @ColumnInfo(defaultValue = "''")  // Add explicit default
    val createdBy: String = "",

    @ColumnInfo(defaultValue = "0")  // Add explicit default
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(defaultValue = "0")  // Add explicit default
    val updatedAt: Long = System.currentTimeMillis(),

    val metadata: Map<String, Any>? = null
)


package io.pawsomepals.app.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID


@Entity(
    tableName = "playdate_requests",
    foreignKeys = [
        ForeignKey(
            entity = Playdate::class,
            parentColumns = ["id"],
            childColumns = ["playdateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playdateId")]
)
data class PlaydateRequest(
    @PrimaryKey
    @ColumnInfo(defaultValue = "''")
    val id: String,

    @ColumnInfo(name = "playdateId", defaultValue = "''")
    val playdateId: String = "",

    @ColumnInfo(name = "matchId", defaultValue = "''")
    val matchId: String = "",

    @ColumnInfo(name = "requesterId", defaultValue = "''")
    val requesterId: String,

    @ColumnInfo(name = "receiverId", defaultValue = "''")
    val receiverId: String,

    @ColumnInfo(name = "suggestedTimeslots", defaultValue = "[]")
    val suggestedTimeslots: List<Long> = emptyList(),

    @ColumnInfo(name = "selectedLocationId", defaultValue = "''")
    val selectedLocationId: String = "",

    @ColumnInfo(name = "status", defaultValue = "'PENDING'")
    val status: RequestStatus = RequestStatus.PENDING,

    @ColumnInfo(name = "lastUpdated", defaultValue = "0")
    val lastUpdated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "preferredTimeOfDay", defaultValue = "''")
    val preferredTimeOfDay: String = "",

    @ColumnInfo(name = "duration", defaultValue = "60")
    val duration: Int = 60,

    @ColumnInfo(name = "notes", defaultValue = "''")
    val notes: String = ""
) {
    companion object {
        fun createFromMatch(
            match: Match,
            suggestedTimeslots: List<Long>,
            selectedLocationId: String
        ): PlaydateRequest {
            return PlaydateRequest(
                id = UUID.randomUUID().toString(),
                matchId = match.id,
                requesterId = match.dog1Id,
                receiverId = match.dog2Id,
                suggestedTimeslots = suggestedTimeslots,
                selectedLocationId = selectedLocationId
            )
        }
    }

    // Member function to convert to Match
    fun toMatch(): Match {
        return Match(
            id = matchId,
            user1Id = requesterId,
            user2Id = receiverId,
            status = MatchStatus.PENDING,
            timestamp = lastUpdated
        )
    }
}

// Extension property for formatted date - move outside the class
@get:RequiresApi(Build.VERSION_CODES.O)
val PlaydateRequest.formattedDate: String
    get() = suggestedTimeslots.firstOrNull()?.let { timestamp ->
        DateTimeFormatter.ofPattern("MMM d, yyyy")
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    } ?: "No date set"
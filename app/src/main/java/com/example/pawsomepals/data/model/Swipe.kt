package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.pawsomepals.data.Converters
import com.google.firebase.database.IgnoreExtraProperties

@Entity(
    tableName = "swipes",
    foreignKeys = [
        ForeignKey(
            entity = Dog::class,
            parentColumns = ["id"],
            childColumns = ["swiperDogId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Dog::class,
            parentColumns = ["id"],
            childColumns = ["swipedDogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["swiperDogId"]),
        Index(value = ["swipedDogId"]),
        Index(value = ["swiperDogId", "swipedDogId"], unique = true)
    ]
)
data class Swipe(
    @PrimaryKey
    var id: String = "", // Changed from Int to String

    // Rest of your properties remain the same
    var swiperId: String = "",
    var swipedId: String = "",
    var swiperDogId: String = "",
    var swipedDogId: String = "",
    var isLike: Boolean = false,
    var compatibilityScore: Double = 0.0,
    @TypeConverters(Converters::class)
    var compatibilityReasons: List<String> = emptyList(),
    var timestamp: Long = System.currentTimeMillis(),
    var expiryTimestamp: Long = System.currentTimeMillis() + DEFAULT_EXPIRY_DURATION,
    var distance: Double? = null,
    var initialLocation: SwipeLocation? = null,
    var swipeDirection: SwipeDirection = SwipeDirection.NONE,
    var superLike: Boolean = false,
    var viewDuration: Long = 0,
    var profileScrollDepth: Int = 0,
    var photosViewed: Int = 0
) {
    // No-argument constructor required by Firebase
    constructor() : this(id = "")

    // Your other methods remain the same
    companion object {
        const val DEFAULT_EXPIRY_DURATION = 7 * 24 * 60 * 60 * 1000L
    }

    fun isExpired(): Boolean = System.currentTimeMillis() > expiryTimestamp

    fun isValidMatch(otherSwipe: Swipe?): Boolean {
        if (otherSwipe == null) return false
        return isLike && otherSwipe.isLike &&
                !isExpired() && !otherSwipe.isExpired() &&
                swiperId == otherSwipe.swipedId &&
                swipedId == otherSwipe.swiperId &&
                swiperDogId == otherSwipe.swipedDogId &&
                swipedDogId == otherSwipe.swiperDogId
    }
}
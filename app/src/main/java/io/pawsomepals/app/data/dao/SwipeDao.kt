package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pawsomepals.app.data.model.Swipe

@Dao
interface SwipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwipe(swipe: Swipe)

    @Query("SELECT * FROM swipes WHERE swiperId = :swiperId AND swipedId = :swipedId")
    suspend fun getSwipe(swiperId: String, swipedId: String): Swipe?

    @Query("SELECT * FROM swipes WHERE swiperId = :swiperId AND isLike = 1")
    suspend fun getLikedProfiles(swiperId: String): List<Swipe>
}
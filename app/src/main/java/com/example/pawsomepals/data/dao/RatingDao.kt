package com.example.pawsomepals.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pawsomepals.data.model.Rating
import kotlinx.coroutines.flow.Flow

@Dao
interface RatingDao {
    @Query("SELECT * FROM ratings WHERE userId = :userId")
    fun getUserRatings(userId: String): Flow<List<Rating>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: Rating)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatings(ratings: List<Rating>)

    @Update
    suspend fun updateRating(rating: Rating)

    @Delete
    suspend fun deleteRating(rating: Rating)

    @Query("DELETE FROM ratings WHERE userId = :userId")
    suspend fun deleteUserRatings(userId: String)

    @Query("SELECT AVG(score) FROM ratings WHERE userId = :userId")
    suspend fun getUserAverageRating(userId: String): Float?
}
package com.example.pawsomepals.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pawsomepals.data.model.Dog

@Dao
interface DogDao {
    @Query("SELECT * FROM dogs WHERE id = :dogId")
    suspend fun getDogById(dogId: String): Dog?

    @Query("SELECT * FROM dogs WHERE ownerId = :ownerId")
    suspend fun getDogByOwnerId(ownerId: String): Dog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDog(dog: Dog)

    @Update
    suspend fun updateDog(dog: Dog)

    @Delete
    suspend fun deleteDog(dog: Dog)

    @Query("SELECT * FROM dogs WHERE id NOT IN (SELECT swipedId FROM swipes WHERE swiperId = :userId) AND ownerId != :userId LIMIT 1")
    suspend fun getNextUnseenDogProfile(userId: String): Dog?
}
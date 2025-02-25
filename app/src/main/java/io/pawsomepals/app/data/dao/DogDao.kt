package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.pawsomepals.app.data.model.Dog

@Dao
interface DogDao {
    @Query("SELECT * FROM dogs WHERE ownerId = :ownerId LIMIT 1")
    suspend fun getDogByOwnerId(ownerId: String): Dog?

    @Query("SELECT * FROM dogs WHERE ownerId = :ownerId")
    suspend fun getAllDogsByOwnerId(ownerId: String): List<Dog>
    @Query("SELECT * FROM dogs WHERE id = :dogId")
    suspend fun getDogById(dogId: String): Dog?

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDog(dog: Dog)

    @Transaction
    suspend fun insertDogWithOwnerCheck(dog: Dog, userDao: UserDao) {
        // First check if user exists
        val user = userDao.getUserById(dog.ownerId)
        if (user == null) {
            throw IllegalStateException("Cannot insert dog: Owner ${dog.ownerId} does not exist")
        }
        insertDog(dog)
    }

    @Update
    suspend fun updateDog(dog: Dog)

    @Delete
    suspend fun deleteDog(dog: Dog)

    @Query("DELETE FROM dogs WHERE id = :dogId")
    suspend fun deleteDogById(dogId: String)

    @Query("SELECT * FROM dogs WHERE id NOT IN (SELECT swipedId FROM swipes WHERE swiperId = :userId) AND ownerId != :userId LIMIT 1")
    suspend fun getNextUnseenDogProfile(userId: String): Dog?
}
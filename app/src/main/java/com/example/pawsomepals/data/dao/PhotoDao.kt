package com.example.pawsomepals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pawsomepals.data.model.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE isUserPhoto = :isUserPhoto")
    fun getPhotos(isUserPhoto: Boolean): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE url = :photoUrl")
    suspend fun deletePhoto(photoUrl: String)

    @Query("DELETE FROM photos WHERE isUserPhoto = :isUserPhoto")
    suspend fun deleteAllPhotos(isUserPhoto: Boolean)

    @Query("SELECT * FROM photos WHERE url = :photoUrl LIMIT 1")
    suspend fun getPhotoByUrl(photoUrl: String): PhotoEntity?
}

package com.example.pawsomepals.data.dao

import androidx.room.*
import com.example.pawsomepals.data.model.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE url = :photoUrl")
    suspend fun deletePhoto(photoUrl: String)

    @Query("""
        SELECT * FROM photos 
        WHERE ownerId = :ownerId AND isUserPhoto = :isUserPhoto 
        ORDER BY `index` ASC
    """)
    fun getPhotosByOwner(ownerId: String, isUserPhoto: Boolean): Flow<List<PhotoEntity>>

    @Query("UPDATE photos SET `index` = :newIndex WHERE url = :photoUrl")
    suspend fun updatePhotoIndex(photoUrl: String, newIndex: Int)

    @Query("DELETE FROM photos WHERE ownerId = :ownerId AND isUserPhoto = :isUserPhoto")
    suspend fun deleteAllPhotos(ownerId: String, isUserPhoto: Boolean)

    @Query("""
        SELECT COUNT(*) 
        FROM photos 
        WHERE ownerId = :ownerId AND isUserPhoto = :isUserPhoto
    """)
    suspend fun getPhotoCount(ownerId: String, isUserPhoto: Boolean): Int

    @Transaction
    suspend fun reorderPhotos(ownerId: String, isUserPhoto: Boolean, newOrder: List<String>) {
        // First mark all existing photos with temporary negative indices
        val currentPhotos = getPhotosByOwner(ownerId, isUserPhoto).collect { photos ->
            photos.forEachIndexed { index, photo ->
                updatePhotoIndex(photo.url, -(index + 1000)) // Using large negative numbers to avoid conflicts
            }
        }

        // Then update to new indices
        newOrder.forEachIndexed { index, url ->
            updatePhotoIndex(url, index)
        }
    }
}
package com.example.pawsomepals.data.repository

import android.net.Uri
import com.example.pawsomepals.data.dao.PhotoDao
import com.example.pawsomepals.data.model.PhotoEntity
import com.example.pawsomepals.data.remote.PhotoApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    private val photoApi: PhotoApi,
    private val photoDao: PhotoDao
) {
    suspend fun uploadPhoto(uri: Uri, isUserPhoto: Boolean): String {
        // Upload to remote storage
        val remoteUrl = photoApi.uploadPhoto(uri, isUserPhoto)

        // Save to local database
        val photoEntity = PhotoEntity(
            id = remoteUrl,  // Assuming the URL can be used as a unique ID
            url = remoteUrl,
            isUserPhoto = isUserPhoto,
            ownerId = "currentUserId"  // Replace with actual user ID
        )
        photoDao.insertPhoto(photoEntity)

        return remoteUrl
    }

    suspend fun deletePhoto(photoUrl: String, isUserPhoto: Boolean) {
        // Delete from remote storage
        photoApi.deletePhoto(photoUrl)

        // Delete from local database
        photoDao.deletePhoto(photoUrl)
    }

    suspend fun getPhotos(isUserPhoto: Boolean): Flow<List<String>> {
        return photoDao.getPhotos(isUserPhoto).map { photos ->
            photos.map { it.url }
        }
    }}
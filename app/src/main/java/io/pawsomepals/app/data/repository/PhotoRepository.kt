package io.pawsomepals.app.data.repository

import android.net.Uri
import android.util.Log
import io.pawsomepals.app.data.dao.PhotoDao
import io.pawsomepals.app.data.model.PhotoEntity
import io.pawsomepals.app.data.remote.PhotoApi
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    private val photoApi: PhotoApi,
    private val photoDao: PhotoDao,
    private val storage: FirebaseStorage
) {
    companion object {
        private const val PROFILE_PICTURES_DIR = "profile_pictures"
        private const val DOG_PHOTOS_DIR = "dog_photos"
        private const val MAX_PHOTO_SIZE = 1024 * 1024 // 1MB
    }

    // Upload progress tracking
    private val _uploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Float>> = _uploadProgress.asStateFlow()

    suspend fun uploadPhoto(
        uri: Uri,
        isUserPhoto: Boolean,
        ownerId: String,
        index: Int = 0
    ): String = withContext(Dispatchers.IO) {
        try {
            val uploadId = UUID.randomUUID().toString()
            val directory = if (isUserPhoto) PROFILE_PICTURES_DIR else DOG_PHOTOS_DIR
            val fileName = "${System.currentTimeMillis()}_$index.jpg"
            val storageRef = storage.reference
                .child(directory)
                .child(ownerId)
                .child(fileName)

            // Start upload with progress tracking
            val uploadTask = storageRef.putFile(uri)
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                updateProgress(uploadId, progress.toFloat() / 100)
            }

            // Wait for upload to complete
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Save to local database
            val photoEntity = PhotoEntity(
                id = downloadUrl,
                url = downloadUrl,
                isUserPhoto = isUserPhoto,
                ownerId = ownerId,
                index = index
            )
            photoDao.insertPhoto(photoEntity)

            // Clear progress
            clearProgress(uploadId)

            downloadUrl
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error uploading photo", e)
            throw e
        }
    }

    suspend fun deletePhoto(photoUrl: String, isUserPhoto: Boolean, ownerId: String) {
        withContext(Dispatchers.IO) {
            try {
                // Delete from Firebase Storage
                storage.getReferenceFromUrl(photoUrl).delete().await()

                // Delete from local database
                photoDao.deletePhoto(photoUrl)
            } catch (e: Exception) {
                Log.e("PhotoRepository", "Error deleting photo", e)
                throw e
            }
        }
    }

    fun getPhotos(ownerId: String, isUserPhoto: Boolean): Flow<List<String>> {
        return photoDao.getPhotosByOwner(ownerId, isUserPhoto)
            .map { photos -> photos.sortedBy { it.index }.map { it.url } }
            .catch { e ->
                Log.e("PhotoRepository", "Error fetching photos", e)
                emit(emptyList())
            }
    }

    suspend fun reorderPhotos(ownerId: String, isUserPhoto: Boolean, newOrder: List<String>) {
        withContext(Dispatchers.IO) {
            newOrder.forEachIndexed { index, url ->
                photoDao.updatePhotoIndex(url, index)
            }
        }
    }

    private fun updateProgress(uploadId: String, progress: Float) {
        val currentProgress = _uploadProgress.value.toMutableMap()
        currentProgress[uploadId] = progress
        _uploadProgress.value = currentProgress
    }

    private fun clearProgress(uploadId: String) {
        val currentProgress = _uploadProgress.value.toMutableMap()
        currentProgress.remove(uploadId)
        _uploadProgress.value = currentProgress
    }
}
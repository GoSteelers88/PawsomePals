package io.pawsomepals.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.data.dao.PhotoDao
import io.pawsomepals.app.data.model.PhotoEntity
import io.pawsomepals.app.data.remote.PhotoApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PhotoRepository @Inject constructor(
    private val photoApi: PhotoApi,
    private val photoDao: PhotoDao,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context,  // Add this

) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  // Add this

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
    suspend fun compressImage(uri: Uri, maxDimension: Int): Uri = withContext(Dispatchers.IO) {
        try {
            // Create a temporary file for the compressed image
            val tempFile = File.createTempFile("compressed_", ".jpg", context.cacheDir)

            // Get input stream from content resolver
            context.contentResolver.openInputStream(uri)?.use { input ->
                // First decode with inJustDecodeBounds=true to check dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)

                // Calculate inSampleSize
                val scaleFactor = calculateScaleFactor(
                    options.outWidth,
                    options.outHeight,
                    maxDimension
                )

                // Decode bitmap with inSampleSize set
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    val compressOptions = BitmapFactory.Options().apply {
                        inSampleSize = scaleFactor
                    }
                    val bitmap = BitmapFactory.decodeStream(input2, null, compressOptions)

                    bitmap?.let { bmp ->
                        FileOutputStream(tempFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        bmp.recycle()
                    }
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error compressing image", e)
            uri // Return original URI if compression fails
        }
    }

    private fun calculateScaleFactor(width: Int, height: Int, maxDimension: Int): Int {
        var scale = 1
        while ((width / scale > maxDimension) || (height / scale > maxDimension)) {
            scale *= 2
        }
        return scale
    }
    fun getOutputFileUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(
            "PHOTO_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            deleteOnExit()
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
    suspend fun uploadDogPhoto(
        dogId: String,
        photoUri: Uri,
        photoIndex: Int = 0,
        onProgress: (Float) -> Unit = {}
    ): String {
        try {
            val fileName = "photo_${photoIndex}_${System.currentTimeMillis()}.jpg"
            val imageRef = storage.reference
                .child(DOG_PHOTOS_DIR)
                .child(dogId)
                .child(fileName)

            val uploadTask = imageRef.putFile(photoUri)

            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                onProgress(progress.toFloat() / 100f)
            }

            val uploadResult = uploadTask.await()
            return uploadResult.storage.downloadUrl.await().toString()

        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error uploading dog photo", e)
            throw e
        }
    }


    suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        try {
            context.cacheDir.listFiles()?.let { files ->
                for (file in files) {
                    if (file.name.startsWith("compressed_") && file.isFile) {
                        try {
                            if (file.exists()) {
                                file.delete()
                                Log.d("PhotoRepository", "Deleted temp file: ${file.name}")
                            }
                        } catch (e: Exception) {
                            Log.e("PhotoRepository", "Error deleting file: ${file.name}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error cleaning up temp files", e)
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
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
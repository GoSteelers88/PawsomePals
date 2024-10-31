package com.example.pawsomepals.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageHandler @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val COMPRESSED_IMAGE_QUALITY = 80
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val CACHE_SIZE = 20 // Number of images to keep in memory
        private const val TEMP_FILE_MAX_AGE = 24 * 60 * 60 * 1000 // 24 hours
    }

    private val memoryCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun processImage(uri: Uri, isProfile: Boolean = false): Uri {
        return withContext(Dispatchers.IO) {
            val cacheKey = uri.toString()
            val cachedBitmap = memoryCache.get(cacheKey)

            if (cachedBitmap != null) {
                return@withContext saveBitmapToFile(cachedBitmap, isProfile)
            }

            val bitmap = loadAndResizeBitmap(uri)
            memoryCache.put(cacheKey, bitmap)
            saveBitmapToFile(bitmap, isProfile)
        }
    }

    private suspend fun loadAndResizeBitmap(uri: Uri): Bitmap {
        return withContext(Dispatchers.IO) {
            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            val width = originalBitmap.width
            val height = originalBitmap.height

            if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
                return@withContext originalBitmap
            }

            val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(width, height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()

            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        }
    }

    private suspend fun saveBitmapToFile(bitmap: Bitmap, isProfile: Boolean): Uri {
        return withContext(Dispatchers.IO) {
            val directory = if (isProfile) {
                File(context.cacheDir, "profile_images").apply { mkdirs() }
            } else {
                File(context.cacheDir, "dog_images").apply { mkdirs() }
            }

            val file = File(directory, "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSED_IMAGE_QUALITY, out)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    fun createImageFile(isProfile: Boolean = false): Pair<File, Uri> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val directory = if (isProfile) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "profile_images")
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "dog_images")
        }.apply { mkdirs() }

        val imageFile = File(directory, "IMG_${timeStamp}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        return Pair(imageFile, uri)
    }

    suspend fun cleanupTempFiles() {
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()

                // Cleanup profile images
                File(context.cacheDir, "profile_images").listFiles()?.forEach { file ->
                    if (currentTime - file.lastModified() > TEMP_FILE_MAX_AGE) {
                        file.delete()
                    }
                }

                // Cleanup dog images
                File(context.cacheDir, "dog_images").listFiles()?.forEach { file ->
                    if (currentTime - file.lastModified() > TEMP_FILE_MAX_AGE) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageHandler", "Error cleaning up temp files", e)
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
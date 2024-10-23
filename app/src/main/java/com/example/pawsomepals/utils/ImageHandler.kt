package com.example.pawsomepals.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageHandler @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val COMPRESSED_IMAGE_QUALITY = 80
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val BUFFER_SIZE = 8192
    }

    suspend fun compressImage(uri: Uri): File {
        return try {
            // Create output file in cache directory
            val outputFile = File(
                context.cacheDir,
                "compressed_${System.currentTimeMillis()}.jpg"
            )

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Wrap with BufferedInputStream for mark/reset support
                BufferedInputStream(inputStream, BUFFER_SIZE).use { bufferedStream ->
                    // First pass - get image dimensions
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    bufferedStream.mark(BUFFER_SIZE)
                    BitmapFactory.decodeStream(bufferedStream, null, options)

                    // Calculate sample size
                    options.apply {
                        inJustDecodeBounds = false
                        inSampleSize = calculateInSampleSize(
                            width = outWidth,
                            height = outHeight,
                            reqWidth = MAX_IMAGE_DIMENSION,
                            reqHeight = MAX_IMAGE_DIMENSION
                        )
                    }

                    // Reset stream for second pass
                    bufferedStream.reset()

                    // Second pass - decode with calculated sample size
                    val bitmap = BitmapFactory.decodeStream(bufferedStream, null, options)
                        ?: throw IllegalStateException("Failed to decode image")

                    // Compress and save
                    FileOutputStream(outputFile).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSED_IMAGE_QUALITY, outputStream)
                    }

                    // Clean up
                    bitmap.recycle()
                }
            } ?: throw IllegalStateException("Failed to open input stream")

            outputFile
        } catch (e: Exception) {
            Log.e("ImageHandler", "Error compressing image", e)
            throw e
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
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

    fun cleanupTempFiles() {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("compressed_") && file.isFile) {
                    try {
                        file.delete()
                        Log.d("ImageHandler", "Deleted temp file: ${file.name}")
                    } catch (e: Exception) {
                        Log.e("ImageHandler", "Error deleting file: ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageHandler", "Error cleaning up temp files", e)
        }
    }
}
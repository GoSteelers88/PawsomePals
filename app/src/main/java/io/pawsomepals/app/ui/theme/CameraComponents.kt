package io.pawsomepals.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CameraUtils(private val context: Context) {
    companion object {
        private const val MAX_IMAGE_DIMENSION = 1920
        private const val JPEG_QUALITY = 85
        private const val PHOTO_PREFIX = "IMG"
        private const val PHOTO_SUFFIX = ".jpg"

        // Different photo types for different use cases
        enum class PhotoType {
            USER_PROFILE,
            DOG_PROFILE,
            DOG_GALLERY,
            TEMPORARY
        }
    }

    /**
     * Creates a properly configured Uri for camera intent
     */
    fun createImageUri(photoType: PhotoType): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = when (photoType) {
            PhotoType.TEMPORARY -> context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            else -> context.getDir(photoType.name.lowercase(), Context.MODE_PRIVATE)
        }

        val photoFile = File.createTempFile(
            "${PHOTO_PREFIX}_${timeStamp}_",
            PHOTO_SUFFIX,
            storageDir
        ).apply {
            if (photoType == PhotoType.TEMPORARY) {
                deleteOnExit()
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    /**
     * Processes and optimizes a captured image
     */
    fun processImage(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)

        // Read EXIF data for rotation
        val exif = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream)
        }

        // Calculate rotation needed
        val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        // Scale bitmap to reasonable size
        val scaledBitmap = scaleBitmap(originalBitmap)

        // Rotate if needed
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
        }

        val processedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            0,
            0,
            scaledBitmap.width,
            scaledBitmap.height,
            matrix,
            true
        )

        // Save processed image
        val outputFile = File.createTempFile(
            PHOTO_PREFIX,
            PHOTO_SUFFIX,
            context.cacheDir
        )

        FileOutputStream(outputFile).use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        // Cleanup
        originalBitmap.recycle()
        scaledBitmap.recycle()
        if (processedBitmap != scaledBitmap) {
            processedBitmap.recycle()
        }

        return outputFile
    }

    /**
     * Scales bitmap while maintaining aspect ratio
     */
    private fun scaleBitmap(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height

        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return original
        }

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (ratio > 1) {
            targetWidth = min(width, MAX_IMAGE_DIMENSION)
            targetHeight = (targetWidth / ratio).toInt()
        } else {
            targetHeight = min(height, MAX_IMAGE_DIMENSION)
            targetWidth = (targetHeight * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
    }

    /**
     * Validates if an image meets quality requirements
     */
    fun validateImage(uri: Uri): Boolean {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val minDimension = min(options.outWidth, options.outHeight)
        val maxDimension = max(options.outWidth, options.outHeight)

        return minDimension >= 480 && maxDimension >= 640 // Minimum acceptable dimensions
    }

    /**
     * Cleans up temporary files
     */
    fun cleanupTempFiles() {
        val tempDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        tempDir?.listFiles()?.forEach { file ->
            if (file.name.startsWith(PHOTO_PREFIX) &&
                System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                file.delete()
            }
        }
    }
}
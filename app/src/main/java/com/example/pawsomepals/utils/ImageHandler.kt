package com.example.pawsomepals.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    suspend fun compressImage(uri: Uri): Uri {
        return withContext(Dispatchers.IO) {
            val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            val compressedBitmap = Bitmap.createScaledBitmap(
                bitmap,
                1024,
                (1024 * (bitmap.height.toFloat() / bitmap.width.toFloat())).toInt(),
                true
            )

            FileOutputStream(outputFile).use { out ->
                compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
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

    class ImageHandler @Inject constructor(
        private val context: Context
    ) {
        fun createImageFile(): Pair<File, Uri> {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

            val imageFile = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
            ).apply {
                deleteOnExit()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            return Pair(imageFile, uri)
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
}
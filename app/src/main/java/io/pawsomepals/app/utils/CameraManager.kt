package io.pawsomepals.app.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.work.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraManager @Inject constructor(
    private val context: Context,
    private val mainExecutor: Executor
) {
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Inactive)
    val cameraState = _cameraState.asStateFlow()

    private val _error = MutableStateFlow<CameraError?>(null)
    val error = _error.asStateFlow()

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    sealed class CameraState {
        object Inactive : CameraState()
        object Preview : CameraState()
        data class Error(val message: String) : CameraState()
        data class Success(val uri: Uri) : CameraState()
    }

    sealed class CameraError {
        object PermissionDenied : CameraError()
        object DeviceNotSupported : CameraError()
        data class InitializationError(val message: String) : CameraError()
        data class CaptureError(val message: String) : CameraError()
    }

    sealed class PermissionState {
        object Granted : PermissionState()
        data class Denied(val permanentlyDenied: Boolean) : PermissionState()
        object NotRequested : PermissionState()
    }

    data class CameraFeatures(
        val hasFlash: Boolean,
        val hasFrontCamera: Boolean,
        val hasBackCamera: Boolean,
        val maxResolution: Size
    )
    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private fun createPhotosDirectory(): File {
        // Create app-specific directory for photos
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "camera_photos").apply {
                    mkdirs()
                }
            }
            else -> {
                File(context.filesDir, "app_temp_photos").apply {
                    mkdirs()
                }
            }
        }
    }

    private val requiredPermissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(Manifest.permission.CAMERA)
        }
        else -> {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getPermissionState(permission: String): PermissionState {
        return when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED ->
                PermissionState.Granted
            shouldShowRequestPermissionRationale(permission) ->
                PermissionState.Denied(permanentlyDenied = false)
            else ->
                PermissionState.Denied(permanentlyDenied = true)
        }
    }

    fun checkCameraRequirements(): List<String> {
        val missingPermissions = mutableListOf<String>()

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            _error.value = CameraError.DeviceNotSupported
            throw IllegalStateException("Device does not have a camera")
        }

        requiredPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }

        return missingPermissions
    }

    @SuppressLint("RestrictedApi")
    fun getCameraFeatures(): CameraFeatures {
        return CameraFeatures(
            hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
            hasFrontCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
            hasBackCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA),
            maxResolution = imageCapture?.camera?.cameraInfo?.let { cameraInfo ->
                val resolution = cameraInfo.sensorRotationDegrees
                Size(resolution, resolution)  // Or use a default size like 1920x1080
            } ?: Size(1920, 1080)  // Default fallback resolution
        )
    }

    @SuppressLint("RestrictedApi")
    suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        try {
            if (!hasRequiredPermissions()) {
                _cameraState.value = CameraState.Error("Camera permissions not granted")
                return
            }

            val cameraProvider = ProcessCameraProvider.getInstance(context).await()

            // Configure preview with correct rotation and surface provider
            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Configure image capture with proper settings
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                _cameraState.value = CameraState.Preview
            } catch (e: Exception) {
                _cameraState.value = CameraState.Error("Failed to bind camera use cases")
            }
        } catch (e: Exception) {
            _cameraState.value = CameraState.Error("Camera initialization failed")
        }
    }
    suspend fun capturePhoto(): Uri? = suspendCancellableCoroutine { continuation ->
        try {
            val photoFile = createImageFile()

            // Create FileProvider URI using the correct authority and path
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).apply {
                setMetadata(ImageCapture.Metadata().apply {
                    isReversedHorizontal = false
                })
            }.build()

            imageCapture?.takePicture(
                outputOptions,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d("CameraManager", "Photo saved successfully: ${photoFile.absolutePath}")
                        _cameraState.value = CameraState.Success(photoUri)
                        continuation.resume(photoUri) {}
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e("CameraManager", "Photo capture failed: ${exc.message}", exc)
                        _cameraState.value = CameraState.Error(exc.message ?: "Photo capture failed")
                        continuation.resume(null) {}
                        // Clean up failed file
                        photoFile.delete()
                    }
                }
            ) ?: run {
                Log.e("CameraManager", "ImageCapture not initialized")
                _cameraState.value = CameraState.Error("Camera not initialized")
                continuation.resume(null) {}
                photoFile.delete()
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Error creating photo file", e)
            _cameraState.value = CameraState.Error("Failed to create image file: ${e.message}")
            continuation.resume(null) {}
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
        val storageDir = createPhotosDirectory()

        return File(
            storageDir,
            "JPEG_${timeStamp}.jpg"
        ).apply {
            // Create parent directories if they don't exist
            parentFile?.mkdirs()
            // Create the file
            createNewFile()
        }
    }

    private fun Context.createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw IOException("Failed to access storage")

        return File.createTempFile(
            "IMG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            parentFile?.mkdirs()
        }
    }

    private fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        val activity = context.findActivity()
        return activity?.shouldShowRequestPermissionRationale(permission) ?: false
    }

    private fun Context.findActivity(): Activity? {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun setImageAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        imageAnalyzer?.setAnalyzer(mainExecutor, analyzer)
    }

    fun cleanup() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageCapture = null
            cameraProvider = null
            _cameraState.value = CameraState.Inactive
        } catch (e: Exception) {
            Log.e("CameraManager", "Cleanup error", e)
        }
    }
}
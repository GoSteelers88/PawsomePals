package com.example.pawsomepals.utils

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import java.io.File

// CameraState class
class CameraState(
    private val context: Context,
    private val imageHandler: ImageHandler,
    val onPhotoTaken: (Uri) -> Unit
) {
    private var currentPhotoUri: Uri? = null

    fun getNewPhotoUri(): Uri {
        val tempFile = File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ).apply {
            deleteOnExit()
        }

        currentPhotoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        return currentPhotoUri!!
    }

    fun handlePhotoTaken(success: Boolean) {
        if (success && currentPhotoUri != null) {
            onPhotoTaken(currentPhotoUri!!)
        }
    }
}

// CameraPermissionState class
class CameraPermissionState {
    var permissionGranted by mutableStateOf(false)
        private set

    fun handlePermissionResult(granted: Boolean) {
        permissionGranted = granted
    }
}

// Composable functions
@Composable
fun rememberCameraState(
    context: Context,
    imageHandler: ImageHandler,
    onPhotoTaken: (Uri) -> Unit
): CameraState {
    return remember(context, imageHandler, onPhotoTaken) {
        CameraState(context, imageHandler, onPhotoTaken)
    }
}

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    return remember { CameraPermissionState() }
}

@Composable
fun rememberCameraLaunchers(
    cameraState: CameraState,
    permissionState: CameraPermissionState,
    onDenied: () -> Unit = {},
    onShowRationale: () -> Unit = {}
): Pair<ActivityResultLauncher<String>, ActivityResultLauncher<Uri>> {
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState.handlePermissionResult(isGranted)
        when {
            isGranted -> { /* Permission granted, camera can be launched */ }
            else -> onDenied()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        cameraState.handlePhotoTaken(success)
    }

    return Pair(permissionLauncher, cameraLauncher)
}

@Composable
fun rememberGalleryLauncher(onImageSelected: (Uri) -> Unit): ActivityResultLauncher<String> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelected(it) }
    }
}
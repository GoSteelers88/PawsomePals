package io.pawsomepals.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.pawsomepals.app.utils.PermissionHelper.openAppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private fun shouldShowRationale(context: Context, permission: String): Boolean {
    return PermissionHelper.shouldShowRequestPermissionRationale(context, permission)
}

class CameraPermissionState {
    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.Initial)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    sealed class PermissionState {
        object Initial : PermissionState()
        object Granted : PermissionState()
        object Denied : PermissionState()
        object PermanentlyDenied : PermissionState()
        data class Error(val message: String) : PermissionState()
    }

    fun handlePermissionResult(granted: Boolean, shouldShowRationale: Boolean = false) {
        _permissionState.value = when {
            granted -> PermissionState.Granted
            !shouldShowRationale -> PermissionState.PermanentlyDenied
            else -> PermissionState.Denied
        }
    }
    // Add this at the top level of CameraUtils.kt
    private fun shouldShowRationale(context: Context, permission: String): Boolean {
        return PermissionHelper.shouldShowRequestPermissionRationale(context, permission)
    }

    companion object {
        fun create(context: Context): CameraPermissionState {
            return CameraPermissionState().apply {
                handlePermissionResult(
                    granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
        }
    }
}


class CameraState(
    private val context: Context,
    private val imageHandler: ImageHandler,
    private val onPhotoTaken: suspend (Uri) -> Unit,
    private val isProfile: Boolean = false
) {
    private var currentPhotoUri: Uri? = null
    private var photoFile: File? = null

    suspend fun preparePhotoCapture(): Uri {
        val (file, uri) = imageHandler.createImageFile(isProfile)
        photoFile = file
        currentPhotoUri = uri
        return uri
    }

    suspend fun handlePhotoTaken(success: Boolean) {
        if (success && currentPhotoUri != null) {
            try {
                val processedUri = imageHandler.processImage(currentPhotoUri!!, isProfile)
                onPhotoTaken(processedUri)
            } catch (e: Exception) {
                Log.e("CameraState", "Error processing photo", e)
                throw e
            } finally {
                photoFile?.delete()
            }
        }
    }

    fun cleanup() {
        photoFile?.delete()
        currentPhotoUri = null
    }
}

@Composable
fun CameraPermissionHandler(
    viewModel: CameraPermissionManager, // Change to use ViewModel instead of State
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {},
    rationaleTitle: String = "Camera Permission Required",
    rationaleText: String = "We need camera access to take photos. Please grant the permission.",
    permanentlyDeniedTitle: String = "Camera Permission Required",
    permanentlyDeniedText: String = "Camera permission has been permanently denied. Please enable it in app settings."
) {
    val context = LocalContext.current
    val state by viewModel.permissionState.collectAsState()
    var showRationale by remember { mutableStateOf(false) }
    var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.handlePermissionResult(isGranted)
    }

    LaunchedEffect(state) {
        when (state) {
            is CameraPermissionManager.PermissionState.Granted -> {
                onPermissionGranted()
            }
            is CameraPermissionManager.PermissionState.RequestPermission -> {
                if (!showRationale) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    showRationale = true
                }
            }
            is CameraPermissionManager.PermissionState.Denied -> {
                showRationale = true
            }
            is CameraPermissionManager.PermissionState.PermanentlyDenied -> {
                showPermanentlyDeniedDialog = true
            }
            else -> {}
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                onPermissionDenied()
            },
            title = { Text(rationaleTitle) },
            text = { Text(rationaleText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        viewModel.onPermissionDenied()
                        onPermissionDenied()
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    if (showPermanentlyDeniedDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermanentlyDeniedDialog = false
                onPermissionDenied()
            },
            title = { Text(permanentlyDeniedTitle) },
            text = { Text(permanentlyDeniedText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermanentlyDeniedDialog = false
                        openAppSettings(context)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermanentlyDeniedDialog = false
                        viewModel.onPermissionDenied()
                        onPermissionDenied()
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }
}

@Composable
private fun RationaleDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PermanentlyDeniedDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,  // Added missing parameter
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {  // Now using the parameter
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Keep existing helper functions
@Composable
fun rememberCameraState(
    context: Context,
    imageHandler: ImageHandler,
    onPhotoTaken: suspend (Uri) -> Unit,
    isProfile: Boolean = false
): CameraState = remember(context, imageHandler, onPhotoTaken, isProfile) {
    CameraState(context, imageHandler, onPhotoTaken, isProfile)
}.also { state ->
    DisposableEffect(state) {
        onDispose {
            state.cleanup()
        }
    }
}

// Keep other existing remember functions...
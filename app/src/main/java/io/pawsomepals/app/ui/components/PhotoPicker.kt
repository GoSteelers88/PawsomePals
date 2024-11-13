package io.pawsomepals.app.ui.components

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.pawsomepals.app.utils.ImageHandler
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PhotoPicker(
    imageHandler: ImageHandler,
    onPhotoSelected: suspend (Uri) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showError by remember { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember { mutableStateOf<Boolean?>(null) }

    // Camera launcher - declare before permission launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            scope.launch {
                try {
                    val processedUri = imageHandler.processImage(tempPhotoUri!!)
                    onPhotoSelected(processedUri)
                    onDismiss()
                } catch (e: Exception) {
                    showError = "Failed to process photo: ${e.message}"
                }
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            tempPhotoUri = createTempPhotoUri(context)
            tempPhotoUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        } else {
            showError = "Camera permission is required to take photos"
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val processedUri = imageHandler.processImage(it)
                    onPhotoSelected(processedUri)
                    onDismiss()
                } catch (e: Exception) {
                    showError = "Failed to process photo: ${e.message}"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Photo Source") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Take Photo")
                }

                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose from Gallery")
                }

                showError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            tempPhotoUri?.path?.let { path ->
                File(path).delete()
            }
        }
    }
}

private fun createTempPhotoUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir("temp_photos")?.apply { mkdirs() }
    val photoFile = File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    ).apply {
        deleteOnExit()
    }

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

@Composable
fun PhotoPickerButton(
    onPhotoSelected: suspend (Uri) -> Unit,
    imageHandler: ImageHandler,
    modifier: Modifier = Modifier
) {
    var showPhotoPicker by remember { mutableStateOf(false) }

    if (showPhotoPicker) {
        PhotoPicker(
            imageHandler = imageHandler,
            onPhotoSelected = onPhotoSelected,
            onDismiss = { showPhotoPicker = false }
        )
    }

    Button(
        onClick = { showPhotoPicker = true },
        modifier = modifier
    ) {
        Text("Change Photo")
    }
}
package com.example.pawsomepals.ui.components



import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pawsomepals.utils.*

@Composable
fun PhotoPicker(
    imageHandler: ImageHandler,
    onPhotoSelected: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cameraState = rememberCameraState(context, imageHandler, onPhotoSelected)
    val permissionState = rememberCameraPermissionState()

    val (permissionLauncher, cameraLauncher) = rememberCameraLaunchers(
        cameraState = cameraState,
        permissionState = permissionState,
        onDenied = {
            // Show permission denied message
            onDismiss()
        }
    )

    val galleryLauncher = rememberGalleryLauncher(onPhotoSelected)

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
                    onClick = {
                        if (permissionState.permissionGranted) {
                            val photoUri = cameraState.getNewPhotoUri()
                            cameraLauncher.launch(photoUri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Take Photo")
                }

                Button(
                    onClick = {
                        galleryLauncher.launch("image/*")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose from Gallery")
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
}

// Example usage:
/*
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    imageHandler: ImageHandler
) {
    var showPhotoPicker by remember { mutableStateOf(false) }

    if (showPhotoPicker) {
        PhotoPicker(
            imageHandler = imageHandler,
            onPhotoSelected = { uri ->
                viewModel.updateUserProfilePicture(uri)
            },
            onDismiss = { showPhotoPicker = false }
        )
    }

    // Rest of your profile screen...
}
*/
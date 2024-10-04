package utils

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun rememberCameraLauncher(onPhotoTaken: (Uri) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            onPhotoTaken(Uri.EMPTY) // Replace Uri.EMPTY with the actual Uri when you implement full camera functionality
        }
    }
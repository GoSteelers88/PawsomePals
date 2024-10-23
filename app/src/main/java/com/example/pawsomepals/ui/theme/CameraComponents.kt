// CameraComponents.kt
package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PhotoOptionsDialog(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,  // Changed from @Composable () -> Unit to () -> Unit
    onChooseFromGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Photo Source") },
        text = {
            Column {
                TextButton(onClick = onTakePhoto) {
                    Text("Take Photo")
                }
                TextButton(onClick = onChooseFromGallery) {
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
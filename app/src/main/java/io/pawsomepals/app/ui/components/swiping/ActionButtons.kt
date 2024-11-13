// ActionButtons.kt
package io.pawsomepals.app.ui.components.swiping

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActionButtons(
    onDislike: () -> Unit,
    onLike: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onDislike,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dislike")
            }
            Button(
                onClick = onLike,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Like")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSchedulePlaydate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Schedule Playdate")
        }
    }
}
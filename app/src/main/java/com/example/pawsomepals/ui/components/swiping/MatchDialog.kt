// MatchDialog.kt
package com.example.pawsomepals.ui.components.swiping

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.viewmodel.SwipingViewModel

@Composable
fun MatchDialog(
    matchDetail: SwipingViewModel.MatchDetail?,
    onDismiss: () -> Unit,
    onSchedulePlaydate: (String) -> Unit
) {
    if (matchDetail != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎉 It's a Match! 🎉",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Profile Image
                    Image(
                        painter = rememberAsyncImagePainter(matchDetail.dog.profilePictureUrl),
                        contentDescription = "Matched dog profile picture",
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .padding(8.dp),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Match Details
                    Text(
                        text = "You matched with ${matchDetail.dog.name}!",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Compatibility Score
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "Compatibility: ${(matchDetail.compatibilityScore * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Compatibility Reasons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Why you'll get along:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        matchDetail.compatibilityReasons.forEach { reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSchedulePlaydate(matchDetail.dog.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Schedule Playdate")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue Browsing")
                }
            }
        )
    }
}

// Optional: Add a preview of the match dialog
@Preview(showBackground = true)
@Composable
fun MatchDialogPreview() {
    val sampleMatchDetail = SwipingViewModel.MatchDetail(
        dog = Dog(
            id = "1",
            name = "Buddy",
            breed = "Golden Retriever",
            age = 3,
            profilePictureUrl = "",
            energyLevel = "High",
            size = "Large"
        ),
        compatibilityScore = 0.85,
        compatibilityReasons = listOf(
            "Similar energy levels",
            "Compatible sizes",
            "Both love to play fetch"
        )
    )

    MaterialTheme {
        MatchDialog(
            matchDetail = sampleMatchDetail,
            onDismiss = {},
            onSchedulePlaydate = {}
        )
    }
}
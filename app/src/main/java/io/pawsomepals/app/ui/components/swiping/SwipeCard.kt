// SwipeCard.kt
package io.pawsomepals.app.ui.components.swiping

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.viewmodel.DogProfileViewModel

@Composable
fun SwipeCard(
    profile: Dog,
    compatibilityState: DogProfileViewModel.CompatibilityState,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val rotation by animateFloatAsState(targetValue = offsetX * 0.05f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(0.75f)
            .offset(x = offsetX.dp)
            .rotate(rotation)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX > 100 -> onSwipeRight()
                            offsetX < -100 -> onSwipeLeft()
                        }
                        offsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = rememberAsyncImagePainter(profile.profilePictureUrl),
                contentDescription = "Dog profile picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Compatibility Info Overlay at the top
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                CompatibilityOverlay(compatibilityState)
            }

            // Profile Info Overlay at the bottom
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                ProfileInfoOverlay(profile)
            }

            // Swipe overlays
            when {
                offsetX > 50 -> LikeOverlay(Modifier.align(Alignment.TopStart))
                offsetX < -50 -> DislikeOverlay(Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@Composable
private fun CompatibilityOverlay(compatibilityState: DogProfileViewModel.CompatibilityState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        when (compatibilityState) {
            is DogProfileViewModel.CompatibilityState.Compatible -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Match Score: ${(compatibilityState.score * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                compatibilityState.reasons.take(2).forEach { reason ->
                    Text(
                        "✓ $reason",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is DogProfileViewModel.CompatibilityState.Incompatible -> {
                Text(
                    "Potential Challenges:",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                compatibilityState.reasons.take(2).forEach { reason ->
                    Text(
                        "• $reason",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> { /* Show nothing for other states */ }
        }
    }
}

@Composable
private fun ProfileInfoOverlay(profile: Dog) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Text(
            text = "${profile.name}, ${profile.age}",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Text(
            text = profile.breed,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        Text(
            text = "Energy: ${profile.energyLevel}, Size: ${profile.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

@Composable
fun LikeOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .size(100.dp)
            .rotate(-30f)
            .background(Color.Green.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
    ) {
        Text(
            "LIKE",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun DislikeOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .size(100.dp)
            .rotate(30f)
            .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
    ) {
        Text(
            "NOPE",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
package com.example.pawsomepals.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.viewmodel.SwipingViewModel

@Composable
fun SwipingScreen(
    viewModel: SwipingViewModel,
    onSchedulePlaydate: (String) -> Unit
) {
    val currentProfile by viewModel.currentProfile.collectAsState()
    val matches by viewModel.matches.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        MatchesRow(matches)
        Box(modifier = Modifier.weight(1f)) {
            currentProfile?.let { profile ->
                SwipeCard(
                    profile = profile,
                    onSwipeLeft = { viewModel.onSwipe(false) },
                    onSwipeRight = { viewModel.onSwipe(true) }
                )
            } ?: Text("No more profiles to show!")
        }
        ActionButtons(
            onDislike = { viewModel.onSwipe(false) },
            onLike = { viewModel.onSwipe(true) },
            onSchedulePlaydate = { currentProfile?.let { onSchedulePlaydate(it.id) } }
        )
    }
}


@Composable
fun SwipeCard(
    profile: DogProfile,
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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
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
            when {
                offsetX > 50 -> LikeOverlay(Modifier.align(Alignment.TopStart))
                offsetX < -50 -> DislikeOverlay(Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@Composable
fun MatchesRow(matches: List<DogProfile>) {
    if (matches.isNotEmpty()) {
        Text(
            "Your Matches",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyRow(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(matches) { match ->
                MatchItem(match)
            }
        }
        Divider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
fun MatchItem(match: DogProfile) {
    Column(
        modifier = Modifier
            .padding(end = 16.dp)
            .width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = rememberAsyncImagePainter(match.profilePictureUrl),
            contentDescription = "Match ${match.name}",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = match.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@Composable
fun ActionButtons(onDislike: () -> Unit, onLike: () -> Unit, onSchedulePlaydate: () -> Unit) {
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
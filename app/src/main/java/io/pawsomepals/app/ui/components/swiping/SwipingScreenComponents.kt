
package io.pawsomepals.app.ui.components.swiping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.SwipeDirection
import io.pawsomepals.app.ui.components.location.LocationEnableRequest
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.SwipingViewModel

@Composable
fun MainSwipingContent(
    currentProfile: Dog?,
    matches: List<Dog>,
    compatibilityState: DogProfileViewModel.CompatibilityState,
    currentMatchDetail: SwipingViewModel.MatchDetail?,
    uiState: SwipingViewModel.SwipingUIState,
    onSwipeWithCompatibility: (String, SwipeDirection) -> Unit,
    onDismissMatch: () -> Unit,
    onSchedulePlaydate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MatchesRow(matches = matches)

        Box(modifier = Modifier.weight(1f)) {
            when (uiState) {
                is SwipingViewModel.SwipingUIState.Loading -> LoadingIndicator()
                is SwipingViewModel.SwipingUIState.LocationNeeded -> LocationEnableRequest()
                is SwipingViewModel.SwipingUIState.Match -> {
                    MatchDialog(
                        matchDetail = currentMatchDetail,
                        onDismiss = onDismissMatch,
                        onSchedulePlaydate = onSchedulePlaydate
                    )
                }
                else -> {
                    currentProfile?.let { profile ->
                        SwipeCard(
                            profile = profile,
                            compatibilityState = compatibilityState,
                            onSwipe = { direction ->
                                onSwipeWithCompatibility(profile.id, direction)
                            }
                        )
                    } ?: EmptyStateMessage()
                }
            }
        }

        ActionButtons(
            onDislike = {
                currentProfile?.let {
                    onSwipeWithCompatibility(it.id, SwipeDirection.LEFT)
                }
            },
            onLike = {
                currentProfile?.let {
                    onSwipeWithCompatibility(it.id, SwipeDirection.RIGHT)
                }
            },
            onSuperLike = {
                currentProfile?.let {
                    onSwipeWithCompatibility(it.id, SwipeDirection.UP)
                }
            },
            onSchedulePlaydate = {
                currentProfile?.let { onSchedulePlaydate(it.id) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwipeCard(
    profile: Dog,
    compatibilityState: DogProfileViewModel.CompatibilityState,
    onSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
    ) {
        Column {
            // Profile Image using SubcomposeAsyncImage
            SubcomposeAsyncImage(
                model = profile.profilePictureUrl ?: profile.photoUrls.firstOrNull(),
                contentDescription = "Profile picture of ${profile.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
            )

            // Dog Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "${profile.name}, ${profile.age}",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = profile.breed,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Info Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    profile.energyLevel.takeIf { it.isNotEmpty() }?.let {
                        DogInfoChip(text = it)
                    }
                    profile.size.takeIf { it.isNotEmpty() }?.let {
                        DogInfoChip(text = it)
                    }
                    profile.isSpayedNeutered?.takeIf { it.isNotEmpty() }?.let {
                        DogInfoChip(text = "Neutered")
                    }
                    profile.exerciseNeeds?.takeIf { it.isNotEmpty() }?.let {
                        DogInfoChip(text = it)
                    }
                }
            }
        }
    }
}
@Composable
private fun DogInfoChip(
    text: String,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = { /* Optional click handling */ },
        label = { Text(text) },
        modifier = modifier
    )
}

@Composable
private fun ActionButtons(
    onDislike: () -> Unit,
    onLike: () -> Unit,
    onSuperLike: () -> Unit,
    onSchedulePlaydate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = onDislike) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "Dislike",
                tint = MaterialTheme.colorScheme.error
            )
        }
        IconButton(onClick = onSuperLike) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Super Like",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        IconButton(onClick = onLike) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Like",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onSchedulePlaydate) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Schedule Playdate",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
@Composable
fun EmptyStateMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No more profiles to show",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MatchesRow(
    matches: List<Dog>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = matches,
            key = { it.id }
        ) { dog ->
            MatchAvatar(
                dog = dog,
                onClick = { /* Handle match click */ }
            )
        }
    }
}


@Composable
private fun MatchAvatar(
    dog: Dog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(64.dp),
        shape = CircleShape,
        onClick = onClick
    ) {
        SubcomposeAsyncImage(
            model = dog.profilePictureUrl ?: dog.photoUrls.firstOrNull(),
            contentDescription = "Match avatar for ${dog.name}",
            contentScale = ContentScale.Crop,
            loading = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dog.name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }
}
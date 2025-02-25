
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.data.model.SwipeDirection
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.ui.components.FilterDialog
import io.pawsomepals.app.ui.components.swiping.DualProfileCard
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.ProfileViewModel
import io.pawsomepals.app.viewmodel.SwipingViewModel
import kotlin.math.roundToInt

@Composable
fun SwipingScreen(
    swipingViewModel: SwipingViewModel,
    dogProfileViewModel: DogProfileViewModel,
    profileViewModel: ProfileViewModel,
    onNavigateToChat: (String) -> Unit,
    locationService: LocationSearchService,
    onNavigateToPlaydate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by swipingViewModel.uiState.collectAsStateWithLifecycle()
    val currentProfile by swipingViewModel.currentProfile.collectAsStateWithLifecycle()
    val currentMatchDetail by swipingViewModel.currentMatchDetail.collectAsStateWithLifecycle()
    val filterState by swipingViewModel.filterState.collectAsStateWithLifecycle()
    val isProcessing by swipingViewModel.isProcessing.collectAsStateWithLifecycle()
    val owner by profileViewModel.userProfile.collectAsStateWithLifecycle()  // This is correctly defined
    val dogOwner by swipingViewModel.ownerProfile.collectAsStateWithLifecycle()


    var showFilterDialog by remember { mutableStateOf(false) }

    // Debug logs for all state changes
    Log.d(
        "SwipingScreen", """
        State Update:
        UI State: ${uiState::class.simpleName}
        Current Profile: ${currentProfile?.id}
        Owner Profile: ${owner?.id}
        Owner Username: ${owner?.username}
        Match Detail: ${currentMatchDetail?.matchId}
        Filter Count: ${filterState.activeFilterCount}
        Processing: $isProcessing
    """.trimIndent()
    )

    if (owner == null) {
        Log.d("SwipingScreen", "Waiting for owner profile to load")
        LoadingState()
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filter Button
        IconButton(
            onClick = {
                Log.d("SwipingScreen", "Filter button clicked")
                showFilterDialog = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Badge(
                modifier = Modifier.size(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(filterState.activeFilterCount.toString())
            }
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is SwipingViewModel.SwipingUIState.Loading,
                is SwipingViewModel.SwipingUIState.ProfileLoading
                -> {
                    Log.d("SwipingScreen", "Showing loading state")
                    LoadingState()
                }

                is SwipingViewModel.SwipingUIState.Success -> {
                    val localDogOwner = dogOwner
                    val localProfile = currentProfile

                    if (localProfile == null || localDogOwner == null) {
                        LoadingState()
                        return@Column
                    }

                    AnimatedContent(
                        targetState = localProfile,
                        modifier = Modifier.fillMaxWidth()
                    ) { targetDog ->
                        Column {
                            SwipeableProfileCard(
                                owner = localDogOwner,
                                dog = targetDog,
                                compatibilityScore = currentMatchDetail?.compatibilityScore ?: 0.0,
                                locationService = locationService,
                                onNavigatePhotos = {
                                    Log.d("SwipingScreen", "Navigate to photos requested")
                                },
                                onSwipe = { direction ->
                                    Log.d(
                                        "SwipingScreen",
                                        "Swipe gesture: $direction for dog: ${targetDog.id}"
                                    )
                                    swipingViewModel.onSwipeWithCompatibility(
                                        targetDog.id,
                                        direction
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            currentMatchDetail?.let { detail ->
                                Log.d(
                                    "SwipingScreen",
                                    "Showing compatibility preview: ${detail.compatibilityScore}"
                                )
                                CompatibilityPreview(
                                    compatibilityScore = detail.compatibilityScore,
                                    reasons = detail.compatibilityReasons,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }

                            SwipeActionButtons(
                                onSwipe = { direction ->
                                    Log.d(
                                        "SwipingScreen",
                                        "Swipe action: $direction for dog: ${targetDog.id}"
                                    )
                                    swipingViewModel.onSwipeWithCompatibility(
                                        targetDog.id,
                                        direction
                                    )
                                },
                                onUndo = {
                                    Log.d("SwipingScreen", "Undo requested")
                                    swipingViewModel.undoLastSwipe()
                                },
                                isProcessing = isProcessing,
                                canUndo = swipingViewModel.canUndo,
                                modifier = Modifier
                                    .padding(top = 32.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                is SwipingViewModel.SwipingUIState.NoMoreProfiles -> {
                    Log.d("SwipingScreen", "No more profiles state")
                    NoMoreProfilesState(
                        onRefresh = {
                            Log.d("SwipingScreen", "Refresh requested")
                            swipingViewModel.refresh()
                        }
                    )
                }

                is SwipingViewModel.SwipingUIState.Match -> {
                    val matchState = uiState as SwipingViewModel.SwipingUIState.Match
                    Log.d(
                        "SwipingScreen",
                        "Showing match dialog: ${matchState.matchDetail.matchId}"
                    )
                    EnhancedMatchDialog(
                        matchDetail = matchState.matchDetail,
                        isSuper = matchState.isSuper,
                        onDismiss = {
                            Log.d("SwipingScreen", "Match dialog dismissed")
                            swipingViewModel.dismissMatch()
                        },
                        onStartChat = {
                            Log.d(
                                "SwipingScreen",
                                "Navigate to chat: ${matchState.matchDetail.chatId}"
                            )
                            onNavigateToChat(matchState.matchDetail.chatId)
                            swipingViewModel.dismissMatch()
                        },
                        onSchedulePlaydate = {
                            Log.d(
                                "SwipingScreen",
                                "Navigate to playdate: ${matchState.matchDetail.matchId}"
                            )
                            onNavigateToPlaydate(matchState.matchDetail.matchId)
                            swipingViewModel.dismissMatch()
                        }
                    )
                }

                is SwipingViewModel.SwipingUIState.Error -> {
                    val errorState = uiState as SwipingViewModel.SwipingUIState.Error
                    Log.e("SwipingScreen", "Error state: ${errorState.message}")
                    ErrorMessage(
                        message = errorState.message,
                        type = errorState.type,
                        onRetry = {
                            Log.d("SwipingScreen", "Retry requested after error")
                            swipingViewModel.refresh()
                        }
                    )
                }

                else -> {
                    Log.d("SwipingScreen", "Unhandled state: ${uiState::class.simpleName}")
                }
            }
        }
    }


    // Filter Dialog
    if (showFilterDialog) {
        Log.d("SwipingScreen", "Showing filter dialog")
        FilterDialog(
            currentFilter = filterState,
            onDismiss = {
                Log.d("SwipingScreen", "Filter dialog dismissed")
                showFilterDialog = false
            },
            onApply = { newFilter ->
                Log.d("SwipingScreen", "Applying new filter with ${newFilter.activeFilterCount} active filters")
                swipingViewModel.updateFilterState(newFilter)
                showFilterDialog = false
            }
        )
    }
}

@Composable
private fun SwipeActionButtons(
    onSwipe: (SwipeDirection) -> Unit,
    onUndo: () -> Unit,
    isProcessing: Boolean,
    canUndo: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canUndo) {
            ActionButton(
                onClick = { if (!isProcessing) onUndo() },
                icon = Icons.Default.Undo,
                contentDescription = "Undo",
                containerColor = Color.White,
                contentColor = Color.Gray,
                borderColor = Color.Gray,
                enabled = !isProcessing
            )
        }

        ActionButton(
            onClick = { if (!isProcessing) onSwipe(SwipeDirection.LEFT) },
            icon = Icons.Default.Close,
            contentDescription = "Dislike",
            containerColor = Color.White,
            contentColor = Color.Red,
            borderColor = Color.Red,
            enabled = !isProcessing
        )

        ActionButton(
            onClick = { if (!isProcessing) onSwipe(SwipeDirection.UP) },
            icon = Icons.Default.Star,
            contentDescription = "Super Like",
            containerColor = Color.White,
            contentColor = Color.Blue,
            borderColor = Color.Blue,
            enabled = !isProcessing
        )

        ActionButton(
            onClick = { if (!isProcessing) onSwipe(SwipeDirection.RIGHT) },
            icon = Icons.Default.Favorite,
            contentDescription = "Like",
            containerColor = Color.White,
            contentColor = Color.Green,
            borderColor = Color.Green,
            enabled = !isProcessing
        )
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) containerColor else Color.Gray.copy(alpha = 0.3f),
        border = BorderStroke(2.dp, if (enabled) borderColor else Color.Gray.copy(alpha = 0.3f)),
        modifier = modifier.size(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) contentColor else Color.Gray.copy(alpha = 0.5f),
            modifier = Modifier
                .padding(12.dp)
                .size(24.dp)
        )
    }
}

@Composable
private fun EnhancedMatchDialog(
    matchDetail: SwipingViewModel.MatchDetail,
    isSuper: Boolean,
    onDismiss: () -> Unit,
    onStartChat: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "It's a Match!",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (isSuper) {
                    Text(
                        "Super Like Match!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("You and ${matchDetail.dog.name} matched!")
                Text(
                    "${(matchDetail.compatibilityScore * 100).toInt()}% Compatible",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Why you match:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        matchDetail.compatibilityReasons.forEach { reason ->
                            Text(
                                "• $reason",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Keep Swiping")
                }
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null)
                        Text("Start Chat")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSchedulePlaydate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Text("Schedule Playdate")
                }
            }
        }
    )
}

@Composable
private fun CompatibilityPreview(
    compatibilityScore: Double,
    reasons: List<MatchReason>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Compatibility Preview",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${(compatibilityScore * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            reasons.take(3).forEach { reason ->
                Text(
                    "• $reason",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
@Composable
private fun SwipeableProfileCard(
    owner: User,
    dog: Dog,
    compatibilityScore: Double,
    locationService: LocationSearchService,
    onNavigatePhotos: () -> Unit,
    onSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX > 100 -> onSwipe(SwipeDirection.RIGHT)  // Right swipe
                            offsetX < -100 -> onSwipe(SwipeDirection.LEFT)  // Left swipe
                            offsetY < -100 -> onSwipe(SwipeDirection.UP)    // Up swipe
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
    ) {
        DualProfileCard(
            owner = owner,
            dog = dog,
            compatibilityScore = compatibilityScore,
            locationService = locationService,
            onNavigatePhotos = onNavigatePhotos,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Finding matches...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun NoMoreProfilesState(
    onRefresh: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No more profiles to show",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    type: SwipingViewModel.ErrorType,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when (type) {
                SwipingViewModel.ErrorType.NETWORK -> Icons.Default.CloudOff
                SwipingViewModel.ErrorType.LOCATION -> Icons.Default.LocationOff
                SwipingViewModel.ErrorType.PERMISSION -> Icons.Default.Lock
                SwipingViewModel.ErrorType.GENERAL -> Icons.Default.Error
            },
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
        }
    }
    }
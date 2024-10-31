// SwipingScreenComponents.kt
package com.example.pawsomepals.ui.components.swiping

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.viewmodel.*
import com.example.pawsomepals.ui.components.location.LocationEnableRequest

@Composable
fun MainSwipingContent(
    currentProfile: Dog?,
    matches: List<Dog>,
    compatibilityState: DogProfileViewModel.CompatibilityState,
    currentMatchDetail: SwipingViewModel.MatchDetail?,
    uiState: SwipingViewModel.SwipingUIState,
    onSwipeWithCompatibility: (String, Boolean) -> Unit,
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
                            onSwipeLeft = { onSwipeWithCompatibility(profile.id, false) },
                            onSwipeRight = { onSwipeWithCompatibility(profile.id, true) }
                        )
                    } ?: EmptyStateMessage()
                }
            }
        }

        ActionButtons(
            onDislike = { currentProfile?.let { onSwipeWithCompatibility(it.id, false) } },
            onLike = { currentProfile?.let { onSwipeWithCompatibility(it.id, true) } },
            onSchedulePlaydate = { currentProfile?.let { onSchedulePlaydate(it.id) } }
        )
    }
}
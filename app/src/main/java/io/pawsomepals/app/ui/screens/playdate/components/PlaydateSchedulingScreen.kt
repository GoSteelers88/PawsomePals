package io.pawsomepals.app.ui.screens.playdate.components

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.pawsomepals.app.data.model.SchedulingStep
import io.pawsomepals.app.viewmodel.EnhancedPlaydateViewModel

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaydateSchedulingScreen(
    matchId: String,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: EnhancedPlaydateViewModel = hiltViewModel()
) {
    Log.d("PlaydateDebug", "PlaydateSchedulingScreen composing with matchId: $matchId")

    val locationState by viewModel.locationState.collectAsState()
    val schedulingState by viewModel.schedulingState.collectAsState()
    val selectedMatch by viewModel.selectedMatch.collectAsState()
    val availableTimeSlots by viewModel.availableTimeSlots.collectAsState()
    val userDogs by viewModel.userDogs.collectAsState()
    val matchedDogs by viewModel.matchedDogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(matchId) {
        Log.d("PlaydateDebug", "LaunchedEffect triggered with matchId: $matchId")
        viewModel.getMatchDetails(matchId)?.collect { match ->
            Log.d("PlaydateDebug", "Got match details: ${match?.match?.id}")
            match?.let { viewModel.startScheduling(it) }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule Playdate") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Progress indicator
                LinearProgressIndicator(
                    progress = when (schedulingState.currentStep) {
                        SchedulingStep.DOG_SELECTION -> 0.2f
                        SchedulingStep.LOCATION -> 0.4f
                        SchedulingStep.DATE -> 0.6f
                        SchedulingStep.TIME -> 0.8f
                        SchedulingStep.REVIEW -> 1.0f
                        SchedulingStep.COMPLETE -> 1.0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Error message if present
                schedulingState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Content based on current step
                when (schedulingState.currentStep) {
                    SchedulingStep.DOG_SELECTION -> DogSelectionContent(
                        matches = matchedDogs,            // This is correct
                        selectedMatch = selectedMatch,     // This is correct
                        onMatchSelected = { matchWithDetails ->
                            viewModel.selectDog(matchWithDetails.otherDog)  // But this needs review
                        },
                        isLoading = isLoading
                    )

                    SchedulingStep.LOCATION -> LocationSelectionContent(
                        locationState = locationState,
                        onLocationSelected = viewModel::selectLocation,
                        onSearch = viewModel::searchLocations,
                        onFilterChange = viewModel::updateLocationFilters  // Add this method to ViewModel
                    )
                    SchedulingStep.DATE -> DateSelectionContent(
                        selectedDate = schedulingState.selectedDate,
                        onDateSelected = viewModel::selectDate
                    )
                    SchedulingStep.TIME -> TimeSelectionContent(
                        availableTimeSlots = availableTimeSlots,
                        selectedTime = schedulingState.selectedTime,
                        onTimeSelected = viewModel::selectTime
                    )
                    SchedulingStep.REVIEW -> ReviewContent(
                        schedulingState = schedulingState,
                        selectedMatch = selectedMatch,
                        onConfirm = {
                            viewModel.createPlaydateRequest()
                            onComplete()
                        },
                        onBack = { viewModel.cancelScheduling() }
                    )
                    SchedulingStep.COMPLETE -> {
                        LaunchedEffect(Unit) {
                            onComplete()
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}


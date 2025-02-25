package io.pawsomepals.app.ui.screens.playdate.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.viewmodel.LocationPermissionHandler
import io.pawsomepals.app.viewmodel.LocationSearchViewModel
import kotlinx.coroutines.launch
import android.content.Intent as AndroidIntent
import android.net.Uri as AndroidUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchScreen(
    viewModel: LocationSearchViewModel = hiltViewModel(),
    onLocationSelected: (DogFriendlyLocation) -> Unit,
    onDismiss: () -> Unit,
    onBackPressed: () -> Unit

) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LocationPermissionHandler(
        onPermissionGranted = viewModel::initializeLocation
    ) {
        val searchState by viewModel.searchState.collectAsState()
        val searchQuery by viewModel.searchQuery.collectAsState()
        val selectedFilters by viewModel.selectedFilters.collectAsState()
        val location by viewModel.currentLocation.collectAsState()
        val snackbarHostState = viewModel.snackbarHostState

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Location") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            if (location == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LocationSearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearch = viewModel::performSearch
                )

                LocationFilters(
                    currentFilters = selectedFilters,
                    onFilterChange = viewModel::updateFilters
                )

                when (val state = searchState) {
                    is LocationSearchViewModel.SearchState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is LocationSearchViewModel.SearchState.Results -> {
                        if (state.locations.isEmpty()) {
                            Text(
                                text = "No locations found",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.locations) { location ->
                                    LocationDetailsCard(
                                        location = location,
                                        onDirections = {
                                            try {
                                                val uri = "google.navigation:q=${location.latitude},${location.longitude}"
                                                val intent = AndroidIntent(AndroidIntent.ACTION_VIEW, AndroidUri.parse(uri)).apply {
                                                    setPackage("com.google.android.apps.maps")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = "Could not open Google Maps",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            }
                                        },
                                        onSave = {
                                            viewModel.saveLocation(location)
                                        },
                                        onClick = { onLocationSelected(location) }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // Handle other states
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationDetailsCard(
    location: DogFriendlyLocation,
    onDirections: () -> Unit,
    onSave: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = location.address,
                style = MaterialTheme.typography.bodyMedium
            )

            // Display amenities
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (location.hasWaterFountain) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Water Fountain") }
                    )
                }
                if (location.hasWasteStations) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Waste Stations") }
                    )
                }
                if (location.hasFencing) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Fenced Area") }
                    )
                }
                if (location.isOffLeashAllowed) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Off-Leash") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDirections,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Get Directions")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Location")
                }
            }
        }
    }
}


sealed class LocationSearchState {
    object Initial : LocationSearchState()
    object Loading : LocationSearchState()
    data class Error(val message: String) : LocationSearchState()
    data class Results(val locations: List<DogFriendlyLocation>) : LocationSearchState()
}

    @Composable
    private fun SuggestionItem(
        suggestion: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Text(
                text = suggestion,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            )
        }
    }

    @Composable
    fun LocationFilters(
        currentFilters: LocationSearchService.LocationFilters,
        onFilterChange: (LocationSearchService.LocationFilters) -> Unit,
        modifier: Modifier = Modifier
    ) {
        var outdoorOnly by remember { mutableStateOf(currentFilters.outdoorOnly) }
        var offLeashOnly by remember { mutableStateOf(currentFilters.offLeashOnly) }
        var verifiedOnly by remember { mutableStateOf(currentFilters.verifiedOnly) }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = outdoorOnly,
                onClick = {
                    outdoorOnly = !outdoorOnly
                    onFilterChange(currentFilters.copy(outdoorOnly = outdoorOnly))
                },
                label = { Text("Outdoor Only") }
            )
            FilterChip(
                selected = offLeashOnly,
                onClick = {
                    offLeashOnly = !offLeashOnly
                    onFilterChange(currentFilters.copy(offLeashOnly = offLeashOnly))
                },
                label = { Text("Off-Leash Area") }
            )
            FilterChip(
                selected = verifiedOnly,
                onClick = {
                    verifiedOnly = !verifiedOnly
                    onFilterChange(currentFilters.copy(verifiedOnly = verifiedOnly))
                },
                label = { Text("Verified Only") }
            )
        }
    }



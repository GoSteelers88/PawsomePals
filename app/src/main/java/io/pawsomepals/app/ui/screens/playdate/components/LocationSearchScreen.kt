package io.pawsomepals.app.ui.screens.playdate.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.ui.screens.playdate.viewmodels.LocationSearchViewModel

@Composable
fun LocationSearchScreen(
    viewModel: LocationSearchViewModel = hiltViewModel(),
    onLocationSelected: (DogFriendlyLocation) -> Unit
) {
    val searchState by viewModel.searchState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilters by viewModel.selectedFilters.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.locations) { location ->
                        LocationDetails(
                            location = location,
                            onDirections = { /* Handle directions */ },
                            onSave = { /* Handle save */ },
                            onClick = { onLocationSelected(location) }
                        )
                    }
                }
            }
            is LocationSearchViewModel.SearchState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            is LocationSearchViewModel.SearchState.AutoComplete -> {
                LazyColumn {
                    items(state.suggestions) { suggestion ->
                        SuggestionItem(
                            suggestion = suggestion,
                            onClick = {
                                viewModel.updateSearchQuery(suggestion)
                                viewModel.performSearch()
                            }
                        )
                    }
                }
            }
            LocationSearchViewModel.SearchState.Initial -> {
                // Show initial state UI if needed
            }
        }
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationDetails(
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
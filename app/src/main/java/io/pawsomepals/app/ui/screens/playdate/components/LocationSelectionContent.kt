package io.pawsomepals.app.ui.screens.playdate.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.LocationState
import io.pawsomepals.app.service.location.LocationSearchService.LocationFilters

@Composable
fun LocationSelectionContent(
    locationState: LocationState,
    onLocationSelected: (DogFriendlyLocation) -> Unit,
    onSearch: (String) -> Unit,
    onFilterChange: (LocationFilters) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search and Filter Section
        var searchQuery by remember { mutableStateOf("") }

        LocationSearchBar(
            query = searchQuery,
            onQueryChange = { newQuery ->
                searchQuery = newQuery
            },
            onSearch = { onSearch(searchQuery) }  // Fixed callback
        )

        Spacer(modifier = Modifier.height(8.dp))

        LocationFilters(onFilterChange = onFilterChange)

        // Content Section
        when (locationState) {
            LocationState.Initial -> InitialContent()
            LocationState.Loading -> LoadingContent()
            is LocationState.Error -> ErrorContent(locationState.message)
            is LocationState.Success -> SuccessContent(
                state = locationState,
                onLocationSelected = onLocationSelected
            )
            is LocationState.SearchResults -> SearchResultsContent(
                state = locationState,
                onLocationSelected = onLocationSelected
            )
        }
    }
}

@Composable
private fun InitialContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Search for a location or select from recommendations",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuccessContent(
    state: LocationState.Success,
    onLocationSelected: (DogFriendlyLocation) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Optimal Location
        state.optimalLocation?.let { optimal ->
            item {
                LocationSection(
                    title = "Best Meeting Point",
                    subtitle = "Recommended location for both of you",
                    locations = listOf(optimal),
                    onLocationSelected = onLocationSelected
                )
            }
        }

        // Dog Parks
        val dogParks = state.nearbyLocations.filter { it.isDogPark }
        if (dogParks.isNotEmpty()) {
            item {
                LocationSection(
                    title = "Dog Parks",
                    subtitle = "Dedicated spaces for dogs",
                    locations = dogParks,
                    onLocationSelected = onLocationSelected
                )
            }
        }

        // Pet-Friendly Places
        val petFriendly = state.nearbyLocations.filter {
            it.servesFood || it.servesDrinks || it.dogMenu || it.dogTreats
        }
        if (petFriendly.isNotEmpty()) {
            item {
                LocationSection(
                    title = "Pet-Friendly Places",
                    subtitle = "Venues that welcome dogs",
                    locations = petFriendly,
                    onLocationSelected = onLocationSelected
                )
            }
        }

        // Other Parks
        val otherParks = state.nearbyLocations.filter {
            !it.isDogPark && it.placeTypes.any { type ->
                type.contains("park", ignoreCase = true)
            }
        }
        if (otherParks.isNotEmpty()) {
            item {
                LocationSection(
                    title = "Parks & Open Spaces",
                    subtitle = "Public areas for walks",
                    locations = otherParks,
                    onLocationSelected = onLocationSelected
                )
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    state: LocationState.SearchResults,
    onLocationSelected: (DogFriendlyLocation) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Search Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        state.predictions.forEach { prediction ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = prediction.getPrimaryText(null).toString(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = prediction.getSecondaryText(null).toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSection(
    title: String,
    subtitle: String,
    locations: List<DogFriendlyLocation>,
    onLocationSelected: (DogFriendlyLocation) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        locations.forEach { location ->
            LocationCard(
                location = location,
                onClick = { onLocationSelected(location) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationCard(
    location: DogFriendlyLocation,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = location.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                location.distance?.let { distance ->
                    Text(
                        text = "${String.format("%.1f", distance)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Location Features
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                location.rating?.let {
                    SmallChip(text = "★ $it")
                }
                if (location.isOffLeashAllowed) SmallChip(text = "Off-leash")
                if (location.hasWaterFountain) SmallChip(text = "Water")
                if (location.hasParking) SmallChip(text = "Parking")
                if (location.hasOutdoorSeating) SmallChip(text = "Outdoor")
            }
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
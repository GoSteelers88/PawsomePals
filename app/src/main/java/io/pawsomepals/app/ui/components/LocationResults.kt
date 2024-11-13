package io.pawsomepals.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.data.model.DogFriendlyLocation

@Composable
fun LocationResults(
    nearbyLocations: List<DogFriendlyLocation>,
    recommendedLocations: List<DogFriendlyLocation>,
    onLocationSelected: (DogFriendlyLocation) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Recommended Locations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        items(recommendedLocations) { location ->
            LocationCard(
                location = location,
                onClick = { onLocationSelected(location) }
            )
        }

        item {
            Text(
                text = "Nearby Locations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        items(nearbyLocations) { location ->
            LocationCard(
                location = location,
                onClick = { onLocationSelected(location) }
            )
        }
    }
}

@Composable
private fun LocationCard(
    location: DogFriendlyLocation,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = location.address,
                style = MaterialTheme.typography.bodyMedium
            )
            if (location.rating != null) {
                Text(
                    text = "Rating: ${location.rating}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
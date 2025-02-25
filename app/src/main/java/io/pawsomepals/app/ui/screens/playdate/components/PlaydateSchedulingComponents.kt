package io.pawsomepals.app.ui.screens.playdate.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.SchedulingState
import io.pawsomepals.app.data.model.TimeSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DogSelectionContent(
    matches: List<Match.MatchWithDetails>,
    selectedMatch: Match.MatchWithDetails?,
    onMatchSelected: (Match.MatchWithDetails) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Select Dog to Meet",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (matches.isEmpty()) {
            Text(
                text = "No matches found. Match with some dogs first!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matches) { matchWithDetails ->
                    MatchCard(
                        match = matchWithDetails,
                        isSelected = matchWithDetails == selectedMatch,
                        onClick = { onMatchSelected(matchWithDetails) }
                    )
                }
            }
        }
    }
}
@Composable
fun MatchCard(
    match: Match.MatchWithDetails,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = match.otherDog.profilePictureUrl,
                contentDescription = "Dog photo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = match.otherDog.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = match.distanceAway,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Match Score: ${(match.match.compatibilityScore * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
fun DogCard(
    dog: Dog,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = dog.profilePictureUrl,
                contentDescription = "Dog photo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dog.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = dog.breed ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun LocationsGrid(
    locations: List<DogFriendlyLocation>,
    recommendedLocations: List<DogFriendlyLocation>,
    optimalLocation: DogFriendlyLocation?,
    onLocationSelected: (DogFriendlyLocation) -> Unit
) {
    LazyColumn {
        items(locations) { location ->
            LocationCard(
                location = location,
                onClick = { onLocationSelected(location) }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        items(recommendedLocations) { location ->
            LocationCard(
                location = location,
                onClick = { onLocationSelected(location) },
                isRecommended = true
            )
        }

        optimalLocation?.let { location ->
            item {
                Text(
                    text = "Optimal Location",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                LocationCard(
                    location = location,
                    onClick = { onLocationSelected(location) },
                    isOptimal = true
                )
            }
        }
    }
}

@Composable
fun LocationCard(
    location: DogFriendlyLocation,
    onClick: () -> Unit,
    isRecommended: Boolean = false,
    isOptimal: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when {
                isOptimal -> {
                    Text(
                        text = "✨ Best Meeting Point",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isRecommended -> {
                    Text(
                        text = "Recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

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
    }
}

@Composable
fun DateSelectionContent(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) = Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "Select Date",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    // Note: CalendarView implementation needed
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TimeSelectionContent(
    availableTimeSlots: List<TimeSlot>,
    selectedTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit
) = Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "Select Time",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(availableTimeSlots) { timeSlot ->
            TimeSlotChip(
                timeSlot = timeSlot,
                isSelected = selectedTime == timeSlot.startTime,
                onClick = { onTimeSelected(timeSlot.startTime) }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TimeSlotChip(
    timeSlot: TimeSlot,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeSlot.startTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReviewContent(
    schedulingState: SchedulingState,
    selectedMatch: Match.MatchWithDetails?,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) = Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    Text(
        text = "Review Details",
        style = MaterialTheme.typography.headlineSmall
    )

    selectedMatch?.let { MatchDetailsCard(it) }
    schedulingState.selectedLocation?.let { LocationDetailsCard(it) }
    DateTimeDetailsCard(schedulingState)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f)
        ) { Text("Back") }

        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f)
        ) { Text("Confirm") }
    }
}

@Composable
fun MatchDetailsCard(match: Match.MatchWithDetails) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Playdate with ${match.otherDog.name}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Distance: ${match.distanceAway}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun LocationDetailsCard(location: DogFriendlyLocation) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Location",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = location.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = location.address,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateTimeDetailsCard(schedulingState: SchedulingState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Date & Time",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = buildString {
                    append(schedulingState.selectedDate?.format(
                        DateTimeFormatter.ofPattern("MMM dd, yyyy")
                    ) ?: "Date not selected")
                    append(" at ")
                    append(schedulingState.selectedTime?.format(
                        DateTimeFormatter.ofPattern("hh:mm a")
                    ) ?: "Time not selected")
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
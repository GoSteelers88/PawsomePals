package io.pawsomepals.app.ui.theme

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.service.weather.WeatherInfo
import io.pawsomepals.app.viewmodel.PlaydateLocationViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun PlaydateScreen(
    viewModel: PlaydateLocationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        TopBar(
            weather = state.weather,
            showFilters = state.showFilters,
            onFilterClick = { viewModel.toggleFilters() },
            onVenueTypeSelect = { type ->
                viewModel.onFilterChange(state.filters.copy(
                    venueTypes = if (state.filters.venueTypes.contains(type)) {
                        state.filters.venueTypes - type
                    } else {
                        state.filters.venueTypes + type
                    }
                ))
            }
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Filters Panel
            AnimatedVisibility(visible = state.showFilters) {
                FiltersPanel(
                    filters = state.filters,
                    onFiltersChanged = viewModel::onFilterChange,
                    onClose = { viewModel.toggleFilters() }
                )
            }

            // Map Area (Google Maps will be integrated here)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Map component will be added here
            }

            // Location List
            LocationList(
                locations = state.locations,
                onSchedulePlaydate = { location ->
                    viewModel.selectLocation(location)
                    viewModel.toggleSchedulingModal()
                }
            )
        }
    }

    // Scheduling Dialog
    if (state.showSchedulingModal) {
        SchedulingDialog(
            selectedLocation = state.selectedLocation,
            userDogs = state.userDogs,
            onConfirm = { dogId, dateTime ->
                viewModel.schedulePlaydate(
                    location = state.selectedLocation!!,
                    dateTime = dateTime,
                    dogId = dogId
                )
            },
            onDismiss = { viewModel.toggleSchedulingModal() }
        )
    }
}

@Composable
private fun TopBar(
    weather: WeatherInfo?,
    showFilters: Boolean,
    onFilterClick: () -> Unit,
    onVenueTypeSelect: (DogFriendlyLocation.VenueType) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter Button
                OutlinedButton(onClick = onFilterClick) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filters",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Filters")
                }

                // Venue Type Buttons
                VenueTypeButton(
                    type = DogFriendlyLocation.VenueType.DOG_PARK,
                    icon = Icons.Default.Pets,
                    backgroundColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32),
                    onClick = onVenueTypeSelect
                )
                VenueTypeButton(
                    type = DogFriendlyLocation.VenueType.RESTAURANT,
                    icon = Icons.Default.WbSunny,
                    backgroundColor = Color(0xFFE3F2FD),
                    contentColor = Color(0xFF1565C0),
                    onClick = onVenueTypeSelect
                )
                VenueTypeButton(
                    type = DogFriendlyLocation.VenueType.CAFE,
                    icon = Icons.Default.Cloud,
                    backgroundColor = Color(0xFFF3E5F5),
                    contentColor = Color(0xFF6A1B9A),
                    onClick = onVenueTypeSelect
                )
            }

            // Weather Display
            weather?.let {
                Text(
                    text = "Weather: ${it.getDisplayTemp()} ${it.condition} ${it.getWeatherEmoji()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FiltersPanel(
    filters: PlaydateLocationViewModel.LocationFilters,
    onFiltersChanged: (PlaydateLocationViewModel.LocationFilters) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(256.dp)
            .fillMaxHeight(),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Distance Slider
            Text(
                text = "Distance",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = filters.distance,
                onValueChange = {
                    onFiltersChanged(filters.copy(distance = it))
                },
                valueRange = 1f..10f,
                steps = 18
            )
            Text(
                text = "${filters.distance.toInt()} miles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Venue Types
            Text(
                text = "Venue Types",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            DogFriendlyLocation.VenueType.values().forEach { venueType ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = filters.venueTypes.contains(venueType),
                        onCheckedChange = { checked ->
                            onFiltersChanged(filters.copy(
                                venueTypes = if (checked) {
                                    filters.venueTypes + venueType
                                } else {
                                    filters.venueTypes - venueType
                                }
                            ))
                        }
                    )
                    Text(
                        text = venueType.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amenities
            Text(
                text = "Amenities",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            DogFriendlyLocation.Amenity.values().forEach { amenity ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = filters.amenities.contains(amenity),
                        onCheckedChange = { checked ->
                            onFiltersChanged(filters.copy(
                                amenities = if (checked) {
                                    filters.amenities + amenity
                                } else {
                                    filters.amenities - amenity
                                }
                            ))
                        }
                    )
                    Text(
                        text = amenity.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationList(
    locations: List<DogFriendlyLocation>,
    onSchedulePlaydate: (DogFriendlyLocation) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(384.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Nearby Locations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${locations.size} dog-friendly places found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Location Cards
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(locations) { location ->
                    LocationCard(
                        location = location,
                        onScheduleClick = { onSchedulePlaydate(location) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    location: DogFriendlyLocation,
    onScheduleClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                location.rating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = location.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Amenity Tags
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (location.isOffLeashAllowed) {
                    AmenityTag(text = "Off-leash")
                }
                if (location.hasWaterFountain) {
                    AmenityTag(text = "Water")
                }
                if (location.hasParking) {
                    AmenityTag(text = "Parking")
                }
                if (location.hasOutdoorSeating) {
                    AmenityTag(text = "Outdoor Seating")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onScheduleClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule Playdate")
            }
        }
    }
}

@Composable
private fun VenueTypeButton(
    type: DogFriendlyLocation.VenueType,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    onClick: (DogFriendlyLocation.VenueType) -> Unit
) {
    IconButton(
        onClick = { onClick(type) },
        modifier = Modifier
            .size(40.dp)
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.name,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AmenityTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@SuppressLint("NewApi")
@Composable
private fun SchedulingDialog(
    selectedLocation: DogFriendlyLocation?,
    userDogs: List<Dog>,
    onConfirm: (dogId: String, dateTime: LocalDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDog by remember { mutableStateOf<Dog?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(LocalTime.of(12, 0)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(500.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Title
                Text(
                    text = selectedLocation?.let { "Schedule Playdate at ${it.name}" }
                        ?: "Schedule Playdate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Dog Selection
                Text(
                    text = "Select Dog",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(userDogs) { dog ->
                        DogSelectionCard(
                            dog = dog,
                            isSelected = selectedDog == dog,
                            onClick = { selectedDog = dog }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Date Selection
                Text(
                    text = "Select Date",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                DatePicker(
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Time Selection
                Text(
                    text = "Select Time",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TimePicker(
                    selectedTime = selectedTime,
                    onTimeSelected = { selectedTime = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            selectedDog?.let { dog ->
                                val dateTime = LocalDateTime.of(selectedDate, selectedTime)
                                onConfirm(dog.id, dateTime)
                            }
                        },
                        enabled = selectedDog != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Schedule")
                    }
                }
            }
        }
    }
}

@Composable
private fun DogSelectionCard(
    dog: Dog,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dog Image
            AsyncImage(
                model = dog.profilePictureUrl,
                contentDescription = "${dog.name}'s profile picture",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dog.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@SuppressLint("NewApi")
@Composable
private fun DatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    // Implement custom date picker or use a library
    // For now, using a placeholder implementation
    OutlinedTextField(
        value = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
        onValueChange = { /* Handle date input */ },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Date") },
        readOnly = true,
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = "Select date"
            )
        }
    )
}

@SuppressLint("NewApi")
@Composable
private fun TimePicker(
    selectedTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit
) {
    // Implement custom time picker or use a library
    // For now, using a placeholder implementation
    OutlinedTextField(
        value = selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
        onValueChange = { /* Handle time input */ },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Time") },
        readOnly = true,
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Select time"
            )
        }
    )
}
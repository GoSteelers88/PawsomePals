package io.pawsomepals.app.ui.screens



import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pawsomepals.app.ui.components.common.LoadingScreen
import io.pawsomepals.app.ui.components.location.LocationPermissionRequest
import io.pawsomepals.app.ui.components.location.LocationSettingsRequest
import io.pawsomepals.app.ui.components.swiping.MainSwipingContent
import io.pawsomepals.app.utils.PermissionHelper
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.LocationPermissionViewModel
import io.pawsomepals.app.viewmodel.SwipingViewModel


data class FilterState(
    val maxDistance: Double = 50.0,
    val energyLevels: List<String> = listOf("ANY"),
    val minAge: Int = 0,
    val maxAge: Int = 20,
    val selectedBreeds: List<String> = listOf("ANY"),
    val size: List<String> = listOf("ANY"),
    val gender: List<String> = listOf("ANY"),
    val isNeutered: Boolean? = null,
    val hasVaccinations: Boolean? = null,
    val showFilter: Boolean = false
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            (maxDistance != 50.0).takeIf { it },
            (energyLevels != listOf("ANY")).takeIf { it },
            ((minAge != 0 || maxAge != 20)).takeIf { it },
            (selectedBreeds != listOf("ANY")).takeIf { it },
            (size != listOf("ANY")).takeIf { it },
            (gender != listOf("ANY")).takeIf { it },
            isNeutered,
            hasVaccinations
        ).size
}

@Composable
fun SwipingScreen(
    viewModel: SwipingViewModel,
    dogProfileViewModel: DogProfileViewModel,
    locationPermissionViewModel: LocationPermissionViewModel,
    onSchedulePlaydate: (String) -> Unit
) {
    var showFilterDialog by remember { mutableStateOf(false) }
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) {
        context as? FragmentActivity
            ?: throw IllegalStateException("SwipingScreen must be used within a FragmentActivity")
    }

    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle(null)
    val matches by viewModel.matches.collectAsStateWithLifecycle(emptyList())
    val compatibilityState by dogProfileViewModel.compatibilityState.collectAsStateWithLifecycle()
    val currentMatchDetail by viewModel.currentMatchDetail.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by locationPermissionViewModel.locationPermissionState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        when {
            isGranted -> locationPermissionViewModel.checkLocationPermission(activity)
            !PermissionHelper.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> locationPermissionViewModel.updatePermissionState(
                LocationPermissionViewModel.LocationPermissionState.PermanentlyDenied
            )
            else -> locationPermissionViewModel.updatePermissionState(
                LocationPermissionViewModel.LocationPermissionState.Denied
            )
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionViewModel.checkLocationPermission(activity)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (permissionState) {
            LocationPermissionViewModel.LocationPermissionState.Initial -> {
                LoadingScreen()
            }
            LocationPermissionViewModel.LocationPermissionState.Denied,
            LocationPermissionViewModel.LocationPermissionState.RequiresRationale -> {
                LocationPermissionRequest(onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                })
            }
            LocationPermissionViewModel.LocationPermissionState.PermanentlyDenied -> {
                LocationSettingsRequest(onOpenSettings = {
                    PermissionHelper.openAppSettings(activity)
                })
            }
            LocationPermissionViewModel.LocationPermissionState.Granted -> {
                MainSwipingContent(
                    currentProfile = currentProfile,
                    matches = matches,
                    compatibilityState = compatibilityState,
                    currentMatchDetail = currentMatchDetail,
                    uiState = uiState,
                    onSwipeWithCompatibility = viewModel::onSwipeWithCompatibility,
                    onDismissMatch = viewModel::dismissMatch,
                    onSchedulePlaydate = onSchedulePlaydate
                )
            }
        }

        FilterButton(
            onClick = { showFilterDialog = true },
            activeFilterCount = filterState.activeFilterCount,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
    }

    if (showFilterDialog) {
        FilterDialog(
            currentFilter = filterState,
            onDismiss = { showFilterDialog = false },
            onApply = { newFilter ->
                viewModel.updateFilterState(newFilter)
                showFilterDialog = false
            }
        )
    }
}

@Composable
fun FilterDialog(
    currentFilter: FilterState,
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit
) {
    var filterState by remember { mutableStateOf(currentFilter) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        title = {
            Text(
                "Filter Matches",
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp)
            ) {
                // Distance Section
                FilterSection(title = "Distance") {
                    Text("Maximum Distance: ${filterState.maxDistance.toInt()} km")
                    Slider(
                        value = filterState.maxDistance.toFloat(),
                        onValueChange = { filterState = filterState.copy(maxDistance = it.toDouble()) },
                        valueRange = 1f..100f
                    )
                }

                // Energy Level Section
                FilterSection(title = "Energy Level") {
                    FilterChipGroup(
                        options = listOf("LOW", "MEDIUM", "HIGH", "ANY"),
                        selectedOptions = filterState.energyLevels,
                        onSelectionChanged = { filterState = filterState.copy(energyLevels = it) }
                    )
                }

                // Age Range Section
                FilterSection(title = "Age") {
                    Text("${filterState.minAge} - ${filterState.maxAge} years")
                    RangeSlider(
                        value = filterState.minAge.toFloat()..filterState.maxAge.toFloat(),
                        onValueChange = { range ->
                            filterState = filterState.copy(
                                minAge = range.start.toInt(),
                                maxAge = range.endInclusive.toInt()
                            )
                        },
                        valueRange = 0f..20f
                    )
                }

                // Size Section
                FilterSection(title = "Size") {
                    FilterChipGroup(
                        options = listOf("SMALL", "MEDIUM", "LARGE", "ANY"),
                        selectedOptions = filterState.size,
                        onSelectionChanged = { filterState = filterState.copy(size = it) }
                    )
                }

                // Additional Filters
                FilterSection(title = "Additional Filters") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = filterState.isNeutered ?: false,
                            onCheckedChange = { filterState = filterState.copy(isNeutered = it) }
                        )
                        Text("Neutered", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = filterState.hasVaccinations ?: false,
                            onCheckedChange = { filterState = filterState.copy(hasVaccinations = it) }
                        )
                        Text("Vaccinated", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(filterState) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { filterState = FilterState() }) {
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipGroup(
    options: List<String>,
    selectedOptions: List<String>,
    onSelectionChanged: (List<String>) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selectedOptions.contains(option),
                onClick = {
                    val newSelection = if (option == "ANY") {
                        listOf("ANY")
                    } else {
                        selectedOptions.toMutableList().apply {
                            if (contains(option)) {
                                remove(option)
                            } else {
                                remove("ANY")
                                add(option)
                            }
                            if (isEmpty()) add("ANY")
                        }
                    }
                    onSelectionChanged(newSelection)
                },
                label = { Text(option) }
            )
        }
    }
}

@Composable
fun FilterButton(
    onClick: () -> Unit,
    activeFilterCount: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter profiles",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (activeFilterCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .size(16.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = activeFilterCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

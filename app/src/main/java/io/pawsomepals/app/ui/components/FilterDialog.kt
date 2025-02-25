package io.pawsomepals.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    currentFilter: FilterState,
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit
) {
    var filterState by remember { mutableStateOf(currentFilter) }
    val scrollState = rememberScrollState()

    val energyLevels = remember {
        listOf(
            "LOW",
            "MEDIUM",
            "HIGH",
            "ANY"
        )
    }

    val sizes = remember {
        listOf(
            "SMALL",
            "MEDIUM",
            "LARGE",
            "ANY"
        )
    }

    val genders = remember {
        listOf(
            "MALE",
            "FEMALE",
            "ANY"
        )
    }

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


                // Energy Level Section using Dog constants
                FilterSection(title = "Energy Level") {
                    FilterChipGroup(
                        options = energyLevels,
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

                // Size Section using Dog constants
                FilterSection(title = "Size") {
                    FilterChipGroup(
                        options = sizes,
                        selectedOptions = filterState.size,
                        onSelectionChanged = { filterState = filterState.copy(size = it) }
                    )
                }

                // Gender Section using Dog constants
                FilterSection(title = "Gender") {
                    FilterChipGroup(
                        options = genders,
                        selectedOptions = filterState.gender,
                        onSelectionChanged = { filterState = filterState.copy(gender = it) }
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

data class FilterState(
    val maxDistance: Double = 50.0,
    val energyLevels: List<String> = listOf("ANY"),
    val minAge: Int = 0,
    val maxAge: Int = 20,
    val selectedBreeds: List<String> = listOf("ANY"),
    val size: List<String> = listOf("ANY"),
    val gender: List<String> = listOf("ANY"),
    val isNeutered: Boolean? = null,
    val hasVaccinations: Boolean? = null
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
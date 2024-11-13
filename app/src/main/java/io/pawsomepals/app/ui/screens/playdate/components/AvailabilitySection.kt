package io.pawsomepals.app.ui.screens.playdate.components


import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.data.model.TimeSlotUi
import io.pawsomepals.app.utils.DateTimeFormatUtils.timeFormatter
import io.pawsomepals.app.utils.TimeFormatUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AvailabilitySection(
    availableTimeSlots: List<TimeSlotUi>,
    onTimeSlotSelected: (TimeSlotUi) -> Unit,
    onUpdateAvailability: (List<TimeSlotUi>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var timeSlots by remember { mutableStateOf(availableTimeSlots) }

    Column(modifier = modifier.fillMaxWidth()) {
        TimeSlotsList(
            timeSlots = timeSlots,
            onAddTimeSlot = { showAddDialog = true },
            onRemoveTimeSlot = { slot ->
                timeSlots = timeSlots.filter { it.id != slot.id }
                onUpdateAvailability(timeSlots)
            }
        )

        if (showAddDialog) {
            AddTimeSlotDialogString(
                onDismiss = { showAddDialog = false },
                onTimeSlotAdded = { startTime, endTime ->
                    val newSlot = TimeSlotUi(
                        dayOfWeek = LocalDate.now().dayOfWeek,  // You might want to make this configurable
                        startTime = startTime,
                        endTime = endTime
                    )
                    timeSlots = timeSlots + newSlot
                    onUpdateAvailability(timeSlots)
                    showAddDialog = false
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun TimeSlotsList(
    timeSlots: List<TimeSlotUi>,
    onAddTimeSlot: () -> Unit,
    onRemoveTimeSlot: (TimeSlotUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add time slot button
        OutlinedCard(
            onClick = onAddTimeSlot,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add time slot"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Time Slot")
            }
        }

        // List of time slots
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(timeSlots, key = { it.id }) { slot ->
                TimeSlotCard(
                    timeSlot = slot,
                    onRemove = { onRemoveTimeSlot(slot) }
                )
            }
        }

        if (timeSlots.isEmpty()) {
            Text(
                text = "No time slots added yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSlotCard(
    timeSlot: TimeSlotUi,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${LocalTime.parse(timeSlot.startTime, TimeFormatUtils.timeFormatter).format(DateTimeFormatter.ofPattern("h:mm a"))} - " +
                        "${LocalTime.parse(timeSlot.endTime, TimeFormatUtils.timeFormatter).format(DateTimeFormatter.ofPattern("h:mm a"))}",
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove time slot",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun AddTimeSlotDialogString(  // Renamed to differentiate
    onDismiss: () -> Unit,
    onTimeSlotAdded: (String, String) -> Unit
) {
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var showStartTimePicker by remember { mutableStateOf(true) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (showStartTimePicker) {
        TimePickerDialog(
            onDismiss = onDismiss,
            onConfirm = { time ->
                startTime = time
                showStartTimePicker = false
                showEndTimePicker = true
            },
            title = "Select start time"
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            onDismiss = {
                showEndTimePicker = false
                showStartTimePicker = true
            },
            onConfirm = { time ->
                endTime = time
                if (endTime > startTime) {
                    onTimeSlotAdded(
                        startTime.format(timeFormatter),
                        endTime.format(timeFormatter)
                    )
                }
            },
            title = "Select end time"
        )
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    title: String
) {
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Hour picker
                    NumberPicker(
                        value = hour,
                        onValueChange = { hour = it },
                        range = 0..23,
                        formatter = { "%02d".format(it) }
                    )
                    Text(":")
                    // Minute picker
                    NumberPicker(
                        value = minute,
                        onValueChange = { minute = it },
                        range = 0..59,
                        formatter = { "%02d".format(it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(hour, minute))
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    formatter: (Int) -> String = { it.toString() }
) {
    Column {
        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) }
        ) {
            Icon(Icons.Default.KeyboardArrowUp, "Increase")
        }
        Text(
            text = formatter(value),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        IconButton(
            onClick = { if (value > range.first) onValueChange(value - 1) }
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "Decrease")
        }
    }
}


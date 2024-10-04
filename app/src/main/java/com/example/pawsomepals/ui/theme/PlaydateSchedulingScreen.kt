package com.example.pawsomepals.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pawsomepals.data.model.Timeslot
import com.example.pawsomepals.viewmodel.PlaydateViewModel
import com.example.pawsomepals.viewmodel.RequestStatus
import java.time.DayOfWeek
import java.time.LocalDate

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaydateSchedulingScreen(
    viewModel: PlaydateViewModel,
    profileId: String?,
    onBackClick: () -> Unit
) {
    val availableTimeslots by viewModel.availableTimeslots.collectAsState()
    val requestStatus by viewModel.requestStatus.collectAsState()
    var selectedTimeslots by remember { mutableStateOf(emptyList<Int>()) }
    val receiverProfile by viewModel.receiverProfile.collectAsState()

    LaunchedEffect(profileId) {
        profileId?.let { viewModel.loadReceiverProfile(it) }
    }

    LaunchedEffect(requestStatus) {
        if (requestStatus is RequestStatus.Success) {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule Playdate") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            receiverProfile?.let { profile ->
                Text(
                    "Scheduling playdate with ${profile.name}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Select available timeslots:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(availableTimeslots) { timeslot ->
                    TimeslotItem(
                        timeslot = timeslot,
                        isSelected = selectedTimeslots.contains(timeslot.id),
                        onToggle = { isSelected ->
                            selectedTimeslots = if (isSelected) {
                                selectedTimeslots + timeslot.id
                            } else {
                                selectedTimeslots - timeslot.id
                            }
                        }
                    )
                }
            }

            when (requestStatus) {
                is RequestStatus.Loading -> CircularProgressIndicator()
                is RequestStatus.Error -> Text(
                    text = (requestStatus as RequestStatus.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Unit
            }

            Button(
                onClick = {
                    profileId?.let {
                        val selectedDates = selectedTimeslots.map { timeslotId ->
                            availableTimeslots.find { it.id == timeslotId }?.let { timeslot ->
                                LocalDate.now().with(DayOfWeek.of(timeslot.dayOfWeek))
                            } ?: LocalDate.now()
                        }
                        viewModel.sendPlaydateRequest(it, selectedDates)
                    }
                },
                modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
                enabled = profileId != null && selectedTimeslots.isNotEmpty() && requestStatus !is RequestStatus.Loading
            ) {
                Text("Send Request")
            }
        }
    }
}

@Composable
fun TimeslotItem(
    timeslot: Timeslot,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggle
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = "Day: ${getDayName(timeslot.dayOfWeek)}")
            Text(text = "Time: ${timeslot.startTime} - ${timeslot.endTime}")
        }
    }
}

fun getDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unknown"
    }
}
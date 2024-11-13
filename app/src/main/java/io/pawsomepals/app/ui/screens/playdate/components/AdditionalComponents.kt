package io.pawsomepals.app.ui.screens.playdate.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.android.identity.util.UUID
import io.pawsomepals.app.data.model.PlaydateWithDetails
import io.pawsomepals.app.data.model.ReminderSetting
import io.pawsomepals.app.data.model.ReminderType
import io.pawsomepals.app.data.model.TimeSlotUi

@Composable
fun CalendarSyncBanner(
    onManageSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Calendar synced", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onManageSync) {
                Text("Manage")
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UpcomingPlaydatesSection(
    playdates: List<PlaydateWithDetails>,
    onPlaydateClick: (String) -> Unit,
    onSchedulePlaydate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Upcoming Playdates",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onSchedulePlaydate) {
                Text("Schedule New")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playdates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No upcoming playdates",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playdates) { playdate ->
                    PlaydateCard(
                        playdate = playdate,
                        onClick = { onPlaydateClick(playdate.playdate.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotChip(
    timeSlot: TimeSlotUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = "${timeSlot.startTime} - ${timeSlot.endTime}",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        modifier = modifier.fillMaxWidth(),
        enabled = timeSlot.isAvailable
    )
}



@Composable
fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier.padding(16.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    ) {
        Text(message)
    }
}

@Composable
fun ReminderSettings(
    onUpdateReminders: (List<ReminderSetting>) -> Unit,
    modifier: Modifier = Modifier
) {
    var reminders by remember { mutableStateOf<List<ReminderSetting>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Reminder Settings",
            style = MaterialTheme.typography.titleSmall
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReminderOption(
                minutes = 15,
                isSelected = reminders.any { it.minutes == 15 },
                onToggle = { selected ->
                    reminders = if (selected) {
                        reminders + ReminderSetting(
                            id = UUID.randomUUID().toString(),
                            minutes = 15,
                            type = ReminderType.NOTIFICATION
                        )
                    } else {
                        reminders.filterNot { it.minutes == 15 }
                    }
                    onUpdateReminders(reminders)
                }
            )

            ReminderOption(
                minutes = 30,
                isSelected = reminders.any { it.minutes == 30 },
                onToggle = { selected ->
                    reminders = if (selected) {
                        reminders + ReminderSetting(
                            id = UUID.randomUUID().toString(),
                            minutes = 30,
                            type = ReminderType.NOTIFICATION
                        )
                    } else {
                        reminders.filterNot { it.minutes == 30 }
                    }
                    onUpdateReminders(reminders)
                }
            )

            ReminderOption(
                minutes = 60,
                isSelected = reminders.any { it.minutes == 60 },
                onToggle = { selected ->
                    reminders = if (selected) {
                        reminders + ReminderSetting(
                            id = UUID.randomUUID().toString(),
                            minutes = 60,
                            type = ReminderType.NOTIFICATION
                        )
                    } else {
                        reminders.filterNot { it.minutes == 60 }
                    }
                    onUpdateReminders(reminders)
                }
            )
        }
    }
}

@Composable
private fun ReminderOption(
    minutes: Int,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (minutes) {
                15 -> "15 minutes before"
                30 -> "30 minutes before"
                60 -> "1 hour before"
                else -> "$minutes minutes before"
            }
        )
        Switch(
            checked = isSelected,
            onCheckedChange = onToggle
        )
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaydateCard(
    playdate: PlaydateWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Playdate with ${playdate.otherDog.name}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Location: ${playdate.location?.name}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Date: ${playdate.formattedDate}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Status: ${playdate.status}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
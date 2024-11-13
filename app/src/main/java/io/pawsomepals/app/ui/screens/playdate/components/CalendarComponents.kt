package io.pawsomepals.app.ui.screens.playdate.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.data.model.PlaydateWithDetails
import io.pawsomepals.app.data.model.ReminderSetting
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarView(
    selectedDate: LocalDate?,
    playdates: List<PlaydateWithDetails>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Calendar Grid
        WeekCalendarView(
            selectedDate = selectedDate ?: LocalDate.now(),
            onDateSelected = onDateSelected,
            hasPlaydate = { date ->
                playdates.any {
                    it.playdate.scheduledTime.toLocalDate() == date
                }
            }
        )

        // Events for selected date
        selectedDate?.let { date ->
            Text(
                text = "Events on ${date.format(dateFormatter)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )

        }
    }
}
@RequiresApi(Build.VERSION_CODES.O)
private fun Long.toLocalDate(): LocalDate {
    return LocalDate.ofEpochDay(this / (24 * 60 * 60 * 1000))
}

@Composable
fun CalendarSyncSettings(
    isCalendarSynced: Boolean,
    onToggleSync: () -> Unit,
    onUpdateReminders: (List<ReminderSetting>) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Calendar Sync",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = isCalendarSynced,
                    onCheckedChange = { onToggleSync() }
                )
            }

            if (isCalendarSynced) {
                ReminderSettings(
                    onUpdateReminders = onUpdateReminders
                )
            }
        }
    }
}
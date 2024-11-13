package io.pawsomepals.app.ui.screens.playdate.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

@Composable
fun WeekCalendarView(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    hasPlaydate: (LocalDate) -> Boolean,
    modifier: Modifier = Modifier
) {
    val currentDate = LocalDate.now()
    val weekDates = remember(currentDate) {
        (-3..3).map { currentDate.plusDays(it.toLong()) }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDates.forEach { date ->
                DayCell(
                    date = date,
                    isSelected = date == selectedDate,
                    hasPlaydate = hasPlaydate(date),
                    onDateSelected = onDateSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    hasPlaydate: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                hasPlaydate -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = date.dayOfWeek.toString().take(3),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
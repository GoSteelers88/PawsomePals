// DateTimePicker.kt
package io.pawsomepals.app.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DateTimePicker(
    onDateTimeSelected: (LocalDateTime) -> Unit,
    minDate: LocalDate = LocalDate.now(),
    maxDate: LocalDate = LocalDate.now().plusMonths(2),
    modifier: Modifier = Modifier
) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month Navigation
            MonthNavigator(
                currentMonth = currentMonth,
                onPreviousMonth = {
                    if (currentMonth.atDay(1) >= minDate) {
                        currentMonth = currentMonth.minusMonths(1)
                    }
                },
                onNextMonth = {
                    if (currentMonth.atEndOfMonth() <= maxDate) {
                        currentMonth = currentMonth.plusMonths(1)
                    }
                }
            )

            // Calendar Grid
            CalendarGrid(
                yearMonth = currentMonth,
                selectedDate = selectedDate,
                minDate = minDate,
                maxDate = maxDate,
                onDateSelected = { date ->
                    selectedDate = date
                    // If time is already selected, trigger the callback
                    selectedTime?.let { time ->
                        onDateTimeSelected(LocalDateTime.of(date, time))
                    }
                }
            )

            // Time Selection
            TimeBoxSelector(
                selectedTime = selectedTime,
                onTimeSelected = { time ->
                    selectedTime = time
                    // If date is already selected, trigger the callback
                    selectedDate?.let { date ->
                        onDateTimeSelected(LocalDateTime.of(date, time))
                    }
                }
            )

            // Selection Summary
            if (selectedDate != null && selectedTime != null) {
                SelectionSummary(
                    dateTime = LocalDateTime.of(selectedDate!!, selectedTime!!),
                    onClear = {
                        selectedDate = null
                        selectedTime = null
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MonthNavigator(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous month"
            )
        }

        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium
        )

        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next month"
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate?,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfMonth = yearMonth.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value

    // Week days header
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Calendar days
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Empty spaces before first day
        items(firstDayOfWeek - 1) {
            Box(modifier = Modifier.aspectRatio(1f))
        }

        // Days of month
        items(daysInMonth) { day ->
            val date = yearMonth.atDay(day + 1)
            val isSelected = date == selectedDate
            val isToday = date == LocalDate.now()
            val isEnabled = date in minDate..maxDate

            CalendarDay(
                date = date,
                isSelected = isSelected,
                isToday = isToday,
                isEnabled = isEnabled,
                onDateSelected = onDateSelected
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isEnabled: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = isEnabled,
                    onClick = { onDateSelected(date) }
                ),
            shape = MaterialTheme.shapes.small,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isToday -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun TimeBoxSelector(
    selectedTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit
) {
    var hour by remember { mutableIntStateOf(selectedTime?.hour?.rem(12) ?: 12) }
    var minute by remember { mutableIntStateOf(selectedTime?.minute ?: 0) }
    var isPM by remember { mutableStateOf(selectedTime?.hour?.div(12) == 1) }

    fun updateTime() {
        val adjustedHour = when {
            hour == 12 && !isPM -> 0
            hour == 12 && isPM -> 12
            isPM -> hour + 12
            else -> hour
        }
        onTimeSelected(LocalTime.of(adjustedHour, minute))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select Time",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hours
            TimeBox(
                value = if (hour == 0) "12" else hour.toString().padStart(2, '0'),
                label = "Hour",
                onIncrement = {
                    hour = if (hour == 12) 1 else hour + 1
                    updateTime()
                },
                onDecrement = {
                    hour = if (hour == 1) 12 else hour - 1
                    updateTime()
                }
            )

            Text(
                text = ":",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Minutes
            TimeBox(
                value = minute.toString().padStart(2, '0'),
                label = "Minute",
                onIncrement = {
                    minute = (minute + 5) % 60
                    updateTime()
                },
                onDecrement = {
                    minute = if (minute == 0) 55 else (minute - 5)
                    updateTime()
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            // AM/PM
            TimeBox(
                value = if (isPM) "PM" else "AM",
                label = "Period",
                onIncrement = {
                    isPM = !isPM
                    updateTime()
                },
                onDecrement = {
                    isPM = !isPM
                    updateTime()
                }
            )
        }
    }


}

@Composable
private fun TimeBox(
    value: String,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .width(64.dp)
                .animateContentSize(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Increment"
                    )
                }

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrement"
                    )
                }
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SelectionSummary(
    dateTime: LocalDateTime,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Selected Date & Time",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = dateTime.format(
                    DateTimeFormatter.ofPattern("EEEE, MMMM d • h:mm a")
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onClear) {
            Text("Clear")
        }
    }
}
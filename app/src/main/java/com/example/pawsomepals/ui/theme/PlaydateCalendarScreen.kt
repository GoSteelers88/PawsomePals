package com.example.pawsomepals.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pawsomepals.data.model.PlaydateRequest
import com.example.pawsomepals.viewmodel.PlaydateViewModel
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.atStartOfMonth
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaydateCalendarScreen(
    viewModel: PlaydateViewModel,
    onBackClick: () -> Unit
) {
    val currentDate = remember { LocalDate.now() }
    val startDate = remember { currentDate.minusDays(500) }
    val endDate = remember { currentDate.plusDays(500) }
    val state = rememberWeekCalendarState(
        startDate = startDate,
        endDate = endDate,
        firstVisibleWeekDate = currentDate,
        firstDayOfWeek = firstDayOfWeekFromLocale()
    )

    val playdates by viewModel.playdateRequests.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Playdate Calendar") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        WeekCalendar(
            state = state,
            dayContent = { day ->
                Day(
                    date = day.date,
                    isSelected = day.date == currentDate,
                    hasPlaydate = playdates.any { it.suggestedTimeslots.contains(day.date.toEpochDay()) }
                )
            }
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(playdates.filter { it.suggestedTimeslots.contains(state.firstVisibleWeek.days.first().date.toEpochDay()) }) { playdate ->
                PlaydateItem(playdate)
            }
        }
    }
}

@Composable
fun Day(date: LocalDate, isSelected: Boolean, hasPlaydate: Boolean) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                hasPlaydate -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PlaydateItem(playdate: PlaydateRequest) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Playdate with: ${playdate.receiverId}")
            Text("Status: ${playdate.status}")
            Text("Time: ${LocalDate.ofEpochDay(playdate.suggestedTimeslots.firstOrNull() ?: 0).format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "TBD"}")
        }
    }
}
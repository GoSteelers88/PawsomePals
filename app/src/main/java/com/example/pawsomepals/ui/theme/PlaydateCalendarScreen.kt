package com.example.pawsomepals.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pawsomepals.data.model.PlaydateRequest
import com.example.pawsomepals.viewmodel.PlaydateViewModel
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaydateScreen(
    viewModel: PlaydateViewModel,
    onNavigateBack: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    var showCalendarView by remember { mutableStateOf(false) }
    val playdateRequests by viewModel.playdateRequests.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playdates") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCalendarView = !showCalendarView }) {
                        Icon(
                            if (showCalendarView) Icons.Default.List else Icons.Default.CalendarToday,
                            contentDescription = "Toggle View"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSchedulePlaydate) {
                Icon(Icons.Default.Add, contentDescription = "Schedule Playdate")
            }
        }
    ) { innerPadding ->
        if (showCalendarView) {
            PlaydateCalendarView(
                playdateRequests = playdateRequests,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            PlaydateListView(
                playdateRequests = playdateRequests,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PlaydateCalendarView(
    playdateRequests: List<PlaydateRequest>,
    modifier: Modifier = Modifier
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

    Column(modifier = modifier.fillMaxSize()) {
        WeekCalendar(
            state = state,
            dayContent = { day ->
                Day(
                    date = day.date,
                    isSelected = day.date == currentDate,
                    hasPlaydate = playdateRequests.any { it.suggestedTimeslots.contains(day.date.toEpochDay()) }
                )
            }
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(playdateRequests.filter { it.suggestedTimeslots.contains(state.firstVisibleWeek.days.first().date.toEpochDay()) }) { playdate ->
                PlaydateRequestItem(playdate)
            }
        }
    }
}

@Composable
fun PlaydateListView(
    playdateRequests: List<PlaydateRequest>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(playdateRequests) { playdateRequest ->
            PlaydateRequestItem(playdateRequest)
        }
    }
}

@Composable
fun Day(date: LocalDate, isSelected: Boolean, hasPlaydate: Boolean) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
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
fun PlaydateRequestItem(playdateRequest: PlaydateRequest) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Playdate with: ${playdateRequest.receiverId}", style = MaterialTheme.typography.bodyLarge)
            Text("Status: ${playdateRequest.status}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Time: ${
                    LocalDate.ofEpochDay(playdateRequest.suggestedTimeslots.firstOrNull() ?: 0)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "TBD"
                }",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
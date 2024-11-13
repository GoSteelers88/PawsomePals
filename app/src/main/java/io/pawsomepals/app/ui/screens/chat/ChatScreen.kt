package io.pawsomepals.app.ui.screens.chat

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.data.model.CompatibilityPrompt
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateMode
import io.pawsomepals.app.data.model.PlaydateMood
import io.pawsomepals.app.data.model.SafetyChecklist
import io.pawsomepals.app.ui.components.ChatBubbleArea
import io.pawsomepals.app.ui.theme.MessageBubble
import io.pawsomepals.app.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatScreen(
    chatId: String,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onSchedulePlaydate: () -> Unit  // New parameter

) {
    val todaysPlaydates by viewModel.todaysPlaydates.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isFirstMeeting by viewModel.isFirstMeeting.collectAsState()
    val safetyChecklist by viewModel.safetyChecklist.collectAsState()
    val compatibilityPrompts by viewModel.compatibilityPrompts.collectAsState()

    var showWeatherCard by remember { mutableStateOf(false) }
    var showParkCard by remember { mutableStateOf(false) }
    var showMoodBoard by remember { mutableStateOf(false) }


    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun ChatScreen(
        chatId: String,
        modifier: Modifier = Modifier,
        viewModel: ChatViewModel,
        onBackClick: () -> Unit,
        onSchedulePlaydate: () -> Unit
    ) {
        val todaysPlaydates by viewModel.todaysPlaydates.collectAsState()
        val messages by viewModel.messages.collectAsState()
        val isFirstMeeting by viewModel.isFirstMeeting.collectAsState()
        val safetyChecklist by viewModel.safetyChecklist.collectAsState()
        val compatibilityPrompts by viewModel.compatibilityPrompts.collectAsState()

        var showWeatherCard by remember { mutableStateOf(false) }
        var showParkCard by remember { mutableStateOf(false) }
        var showMoodBoard by remember { mutableStateOf(false) }

        LaunchedEffect(chatId) {
            if (chatId.isNotBlank()) {
                viewModel.loadChatMessages(chatId)
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
            TodayPlaydatesSection(
                playdates = todaysPlaydates,
                onAccept = viewModel::acceptPlaydate,
                onReschedule = viewModel::reschedulePlaydate
            )

            // Main Chat Area
            ChatBubbleArea(
                messages = messages,
                weatherCard = { if (showWeatherCard) WeatherCard() },
                parkCard = { if (showParkCard) ParkSuggestionCard() }
            )

            // Mood Board
            if (showMoodBoard) {
                PlaydateMoodBoard(
                    onMoodSelected = { mood ->
                        viewModel.sendPlaydateMood(playdateId = "current_playdate_id", mood = mood)
                    }
                )
            }

            // Quick Actions Bar
            QuickActionsBar(
                onParksClick = { showParkCard = true },
                onTimesClick = { /* Show time picker */ },
                onProfileClick = { /* Show profile */ },
                onWeatherClick = { showWeatherCard = true },
                onSchedulePlaydate = onSchedulePlaydate
            )

            // First Meeting Safety Checklist
            if (isFirstMeeting) {
                SafetyChecklistCard(
                    checklist = safetyChecklist,
                    onChecklistUpdated = viewModel::updateSafetyChecklist
                )
            }

            // Compatibility Prompts
            CompatibilityPrompts(
                prompts = compatibilityPrompts,
                onPromptSelected = viewModel::handlePromptSelection
            )
        }
    }
    }

@Composable
fun ChatBubbleArea(
    messages: List<Message>,
    weatherCard: @Composable () -> Unit,
    parkCard: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        reverseLayout = true
    ) {
        item {
            weatherCard()
            parkCard()
        }

        items(messages.reversed()) { message ->
            MessageBubble(message = message)
        }
    }
}

@Composable
private fun TodayPlaydatesSection(
    playdates: List<Playdate>,
    onAccept: (Playdate) -> Unit,
    onReschedule: (Playdate) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Today's Playdates",
                style = MaterialTheme.typography.titleMedium
            )

            playdates.forEach { playdate ->
                PlaydateItem(
                    playdate = playdate,
                    onAccept = { onAccept(playdate) },
                    onReschedule = { onReschedule(playdate) }
                )
            }
        }
    }
}

@Composable
private fun PlaydateItem(
    playdate: Playdate,
    onAccept: () -> Unit,
    onReschedule: () -> Unit
) {
    val formattedTime = remember(playdate.scheduledTime) {
        SimpleDateFormat("hh:mm a", Locale.getDefault())
            .format(Date(playdate.scheduledTime))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "🎯 $formattedTime - ${playdate.location}",
            style = MaterialTheme.typography.bodyLarge
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Accept")
            }

            Button(
                onClick = onReschedule,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Reschedule")
            }
        }
    }
}

@Composable
private fun WeatherCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Perfect play weather tomorrow!",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "72°F, Sunny, Low pollen count",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Handle suggestion */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Suggest Playdate")
                }
                Button(
                    onClick = { /* Handle time selection */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share Other Time")
                }
            }
        }
    }
}

@Composable
private fun QuickActionsBar(
    onParksClick: () -> Unit,
    onTimesClick: () -> Unit,
    onProfileClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onSchedulePlaydate: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onParksClick) {
                Text("🗺️ Parks")
            }
            IconButton(onClick = onTimesClick) {
                Text("⌚ Times")
            }
            IconButton(onClick = onProfileClick) {
                Text("🦮 Profile")
            }
            IconButton(onClick = onWeatherClick) {
                Text("🌤️ Weather")
            }
            IconButton(onClick = onSchedulePlaydate) {
                Text("📅 Schedule")
            }
        }
    }
}
@Composable
private fun PlaydateMoodBoard(
    onMoodSelected: (PlaydateMood) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What kind of playdate?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MoodOption(
                    text = "🎾 Fetch Game",
                    mode = PlaydateMode.FETCH,
                    onMoodSelected = { onMoodSelected(PlaydateMood.EXCELLENT) }
                )
                MoodOption(
                    text = "🏃‍♂️ High Energy Run",
                    mode = PlaydateMode.HIGH_ENERGY,
                    onMoodSelected = { onMoodSelected(PlaydateMood.GOOD) }
                )
                MoodOption(
                    text = "🦮 Calm Walk",
                    mode = PlaydateMode.CALM_WALK,
                    onMoodSelected = { onMoodSelected(PlaydateMood.NEUTRAL) }
                )
                MoodOption(
                    text = "🐾 Social Play",
                    mode = PlaydateMode.SOCIAL,
                    onMoodSelected = { onMoodSelected(PlaydateMood.NEEDS_IMPROVEMENT) }
                )
            }
        }
    }
}
private fun PlaydateMode.getIcon(): String = when(this) {
    PlaydateMode.FETCH -> "🎾"
    PlaydateMode.HIGH_ENERGY -> "🏃"
    PlaydateMode.CALM_WALK -> "🦮"
    PlaydateMode.SOCIAL -> "🐾"
}
@Composable
private fun ChecklistItem(
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
@Composable
private fun MoodOption(
    text: String,
    mode: PlaydateMode,
    onMoodSelected: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onMoodSelected)
            .padding(8.dp)
    ) {
        Text(text = mode.getIcon(), style = MaterialTheme.typography.headlineMedium)
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}


// Extension function to convert PlaydateMode to PlaydateMood
private fun PlaydateMode.toPlaydateMood(): PlaydateMood = when(this) {
    PlaydateMode.FETCH -> PlaydateMood.EXCELLENT
    PlaydateMode.HIGH_ENERGY -> PlaydateMood.GOOD
    PlaydateMode.CALM_WALK -> PlaydateMood.NEUTRAL
    PlaydateMode.SOCIAL -> PlaydateMood.NEEDS_IMPROVEMENT
}


@Composable
private fun SafetyChecklistCard(
    checklist: SafetyChecklist,
    onChecklistUpdated: (SafetyChecklist) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "First Meetup Safety Checklist",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ChecklistItem(
                text = "Vaccination verified",
                isChecked = checklist.vaccinationVerified,
                onCheckedChange = {
                    onChecklistUpdated(checklist.copy(vaccinationVerified = it))
                }
            )
            ChecklistItem(
                text = "Size compatible",
                isChecked = checklist.sizeCompatible,
                onCheckedChange = {
                    onChecklistUpdated(checklist.copy(sizeCompatible = it))
                }
            )
            ChecklistItem(
                text = "Energy level matched",
                isChecked = checklist.energyLevelMatched,
                onCheckedChange = {
                    onChecklistUpdated(checklist.copy(energyLevelMatched = it))
                }
            )
            ChecklistItem(
                text = "Meeting spot confirmed",
                isChecked = checklist.meetingSpotConfirmed,
                onCheckedChange = {
                    onChecklistUpdated(checklist.copy(meetingSpotConfirmed = it))
                }
            )
            ChecklistItem(
                text = "Backup contact shared",
                isChecked = checklist.backupContactShared,
                onCheckedChange = {
                    onChecklistUpdated(checklist.copy(backupContactShared = it))
                }
            )
        }
    }
}

@Composable
private fun ParkSuggestionCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Central Park Dog Run",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⭐4.8 (420 visits)")
                Text("🐕 12 dogs currently there")
                Text("📍 0.8 miles away")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Open maps */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Get Directions")
                }
                Button(
                    onClick = { /* Share location */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share Location")
                }
            }
        }
    }
}

@Composable
private fun CompatibilityPrompts(
    prompts: List<CompatibilityPrompt>,
    onPromptSelected: (CompatibilityPrompt) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        prompts.forEach { prompt ->
            CompositionLocalProvider {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPromptSelected(prompt) }
                ) {
                    Text(
                        text = prompt.message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
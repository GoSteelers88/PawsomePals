package io.pawsomepals.app.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreferencesScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    var newMatches by remember { mutableStateOf(true) }
    var messages by remember { mutableStateOf(true) }
    var playdateReminders by remember { mutableStateOf(true) }
    var appUpdates by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Preferences") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            SwitchSetting(
                title = "New Matches",
                description = "Receive notifications for new matches",
                checked = newMatches,
                onCheckedChange = {
                    newMatches = it
                    viewModel.updateNotificationPreference("newMatches", it)
                }
            )

            SwitchSetting(
                title = "Messages",
                description = "Receive notifications for new messages",
                checked = messages,
                onCheckedChange = {
                    messages = it
                    viewModel.updateNotificationPreference("messages", it)
                }
            )

            SwitchSetting(
                title = "Playdate Reminders",
                description = "Receive reminders for upcoming playdates",
                checked = playdateReminders,
                onCheckedChange = {
                    playdateReminders = it
                    viewModel.updateNotificationPreference("playdateReminders", it)
                }
            )

            SwitchSetting(
                title = "App Updates",
                description = "Receive notifications about io.pawsomepals.app updates",
                checked = appUpdates,
                onCheckedChange = {
                    appUpdates = it
                    viewModel.updateNotificationPreference("appUpdates", it)
                }
            )
        }
    }
}
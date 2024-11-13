package io.pawsomepals.app.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pawsomepals.app.viewmodel.SettingsViewModel
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Settings") },
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
            when (val state = settingsState) {
                is SettingsViewModel.SettingsState.Success -> {
                    val settings = state.settings
                    SwitchSetting(
                        title = "Profile Visibility",
                        description = "Allow others to view your profile",
                        checked = settings.profileVisibility,
                        onCheckedChange = { viewModel.updatePrivacySetting("profileVisibility", it) }
                    )

                    SwitchSetting(
                        title = "Location Sharing",
                        description = "Share your location for better matches",
                        checked = settings.locationSharing,
                        onCheckedChange = { viewModel.updatePrivacySetting("locationSharing", it) }
                    )

                    SwitchSetting(
                        title = "Data Usage",
                        description = "Allow io.pawsomepals.app to use your data for personalization",
                        checked = settings.dataUsage,
                        onCheckedChange = { viewModel.updatePrivacySetting("dataUsage", it) }
                    )
                }
                is SettingsViewModel.SettingsState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                is SettingsViewModel.SettingsState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
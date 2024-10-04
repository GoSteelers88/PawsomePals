package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.pawsomepals.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsState by viewModel.settingsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (settingsState) {
                is SettingsViewModel.SettingsState.Loading -> {
                    Text("Loading settings...")
                }
                is SettingsViewModel.SettingsState.Success -> {
                    val settings = (settingsState as SettingsViewModel.SettingsState.Success).settings
                    SettingsButton(
                        text = "Privacy Settings",
                        onClick = { navController.navigate("privacy_settings") }
                    )
                    SettingsButton(
                        text = "Notification Preferences",
                        onClick = { navController.navigate("notification_preferences") }
                    )
                    SettingsButton(
                        text = "Account Management",
                        onClick = { navController.navigate("account_management") }
                    )
                }
                is SettingsViewModel.SettingsState.Error -> {
                    Text("Error: ${(settingsState as SettingsViewModel.SettingsState.Error).message}")
                }
            }
        }
    }
}

@Composable
fun SettingsButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(text)
    }
}
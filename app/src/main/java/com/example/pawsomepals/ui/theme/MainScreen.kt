package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.pawsomepals.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    onProfileClick: () -> Unit,
    onDogProfileClick: () -> Unit,
    onSwipeScreenClick: () -> Unit,
    onChatListClick: () -> Unit,
    onSchedulePlaydateClick: () -> Unit,
    onPlaydateRequestsClick: () -> Unit,
    onTrainerClick: () -> Unit,
    onHealthAdvisorClick: () -> Unit,
    onPhotoManagementClick: () -> Unit,
    onRatingClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    username: String
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pawsome Pals!") },
                actions = {
                    IconButton(onClick = onSwipeScreenClick) {
                        Icon(painter = painterResource(id = R.drawable.ic_swipe), contentDescription = "Swipe Screen")
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(painter = painterResource(id = R.drawable.ic_profile), contentDescription = "User Profile")
                    }
                    IconButton(onClick = onNotificationsClick) {
                        Icon(painter = painterResource(id = R.drawable.ic_notifications), contentDescription = "Notifications")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(painter = painterResource(id = R.drawable.ic_settings), contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = onDogProfileClick) {
                    Icon(painter = painterResource(id = R.drawable.ic_dog), contentDescription = "Dog Profile")
                }
                IconButton(onClick = onChatListClick) {
                    Icon(painter = painterResource(id = R.drawable.ic_chat), contentDescription = "Chat List")
                }
                IconButton(onClick = onSchedulePlaydateClick) {
                    Icon(painter = painterResource(id = R.drawable.ic_calendar), contentDescription = "Schedule Playdate")
                }
                IconButton(onClick = onPhotoManagementClick) {
                    Icon(painter = painterResource(id = R.drawable.ic_photo), contentDescription = "Photo Management")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text("Welcome, $username!", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Here you'll see potential playdate matches for your dog.")
            Spacer(modifier = Modifier.height(16.dp))

            MainScreenButton(text = "Go to Swipe Screen", onClick = onSwipeScreenClick, iconResId = R.drawable.ic_swipe)
            MainScreenButton(text = "View Chats", onClick = onChatListClick, iconResId = R.drawable.ic_chat)
            MainScreenButton(text = "Schedule Playdate", onClick = onSchedulePlaydateClick, iconResId = R.drawable.ic_calendar)
            MainScreenButton(text = "View Playdate Requests", onClick = onPlaydateRequestsClick, iconResId = R.drawable.ic_requests)
            MainScreenButton(text = "Dog Trainer", onClick = onTrainerClick, iconResId = R.drawable.ic_trainer)
            MainScreenButton(text = "Health Advisor", onClick = onHealthAdvisorClick, iconResId = R.drawable.ic_health)
            MainScreenButton(text = "Rate Your Experience", onClick = onRatingClick, iconResId = R.drawable.ic_rating)

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Logout")
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            navController.navigate("login") {
                                popUpTo("main_screen") { inclusive = true }
                            }
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    Button(onClick = { showLogoutDialog = false }) {
                        Text("No")
                    }
                }
            )
        }
    }
}

@Composable
fun MainScreenButton(text: String, onClick: () -> Unit, iconResId: Int) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text)
        }
    }
}
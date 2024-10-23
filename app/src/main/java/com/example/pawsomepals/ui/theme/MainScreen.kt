package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import com.example.pawsomepals.viewmodel.ChatViewModel
import com.example.pawsomepals.viewmodel.DogProfileViewModel
import com.example.pawsomepals.viewmodel.HealthAdvisorViewModel
import com.example.pawsomepals.viewmodel.NotificationViewModel
import com.example.pawsomepals.viewmodel.PhotoManagementViewModel
import com.example.pawsomepals.viewmodel.PlaydateViewModel
import com.example.pawsomepals.viewmodel.ProfileViewModel
import com.example.pawsomepals.viewmodel.RatingViewModel
import com.example.pawsomepals.viewmodel.SettingsViewModel
import com.example.pawsomepals.viewmodel.SwipingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    username: String,
    profileViewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    playdateViewModel: PlaydateViewModel,
    chatViewModel: ChatViewModel,
    healthAdvisorViewModel: HealthAdvisorViewModel,
    settingsViewModel: SettingsViewModel,
    photoManagementViewModel: PhotoManagementViewModel,
    ratingViewModel: RatingViewModel,
    notificationViewModel: NotificationViewModel,
    swipingViewModel: SwipingViewModel,
    onLogout: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDogProfileClick: () -> Unit,
    onPlaydateClick: (String) -> Unit,
    onChatClick: (String) -> Unit,
    onHealthAdvisorClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPhotoManagementClick: () -> Unit,
    onRatingClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSwipeClick: () -> Unit,
    onSwipeScreenClick: () -> Unit,
    onChatListClick: () -> Unit,
    onSchedulePlaydateClick: () -> Unit,
    onPlaydateRequestsClick: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pawsome Pals!") },
                actions = {
                    IconButton(onClick = onSwipeClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_swipe),
                            contentDescription = "Swipe Screen"
                        )
                    }
                    IconButton(onClick = { onProfileClick("") }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_profile),
                            contentDescription = "User Profile"
                        )
                    }
                    IconButton(onClick = onNotificationsClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_notifications),
                            contentDescription = "Notifications"
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Increased spacing
        ) {
            // Welcome Section with enhanced styling
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Welcome, $username!",
                    style = MaterialTheme.typography.headlineMedium, // Larger text
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Find the perfect playdate for your furry friend!",
                    style = MaterialTheme.typography.bodyLarge, // Larger body text
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Primary Actions Section
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Main Grid Layout
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp) // Increased spacing
            ) {
                // Top Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainScreenButtonLarge(
                        text = "Find Playdates",
                        onClick = onSwipeScreenClick,
                        iconResId = R.drawable.ic_swipe,
                        modifier = Modifier.weight(1f)
                    )
                    MainScreenButtonLarge(
                        text = "My Dog",
                        onClick = onDogProfileClick,
                        iconResId = R.drawable.ic_dog,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bottom Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MainScreenButtonLarge(
                        text = "Messages",
                        onClick = onChatListClick,
                        iconResId = R.drawable.ic_chat,
                        modifier = Modifier.weight(1f)
                    )
                    MainScreenButtonLarge(
                        text = "Schedule",
                        onClick = onSchedulePlaydateClick,
                        iconResId = R.drawable.ic_calendar,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Additional Features Section
            Text(
                text = "More Features",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Features Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MainScreenButtonMedium(
                        text = "Requests",
                        onClick = onPlaydateRequestsClick,
                        iconResId = R.drawable.ic_requests,
                        modifier = Modifier.weight(1f)
                    )
                    MainScreenButtonMedium(
                        text = "Health",
                        onClick = onHealthAdvisorClick,
                        iconResId = R.drawable.ic_health,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MainScreenButtonMedium(
                        text = "Photos",
                        onClick = onPhotoManagementClick,
                        iconResId = R.drawable.ic_photo,
                        modifier = Modifier.weight(1f)
                    )
                    MainScreenButtonMedium(
                        text = "Rate Us",
                        onClick = onRatingClick,
                        iconResId = R.drawable.ic_rating,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Logout", style = MaterialTheme.typography.titleMedium)
            }
        }

        // Logout Dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout", style = MaterialTheme.typography.titleLarge) },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
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
fun MainScreenButtonLarge(
    text: String,
    onClick: () -> Unit,
    iconResId: Int,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(120.dp) // Increased height
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp) // Larger icon
                    .padding(bottom = 8.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium // Larger text
            )
        }
    }
}

@Composable
fun MainScreenButtonMedium(
    text: String,
    onClick: () -> Unit,
    iconResId: Int,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(80.dp)
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(bottom = 4.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
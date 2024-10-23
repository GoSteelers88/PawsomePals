package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pawsomepals.viewmodel.ProfileViewModel
import com.example.pawsomepals.ui.components.SharedDogProfileField
import com.example.pawsomepals.ui.components.SharedProfileSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDogProfileScreen(
    viewModel: ProfileViewModel,
    dogId: String,
    onNavigateBack: () -> Unit,
    onNavigateToQuestionnaire: (String?) -> Unit
) {
    val dogProfile by viewModel.dogProfile.collectAsStateWithLifecycle()
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(dogId) {
        if (dogId == "new") {
            // For new dogs, navigate to questionnaire
            onNavigateToQuestionnaire(null)
        } else {
            // For existing dogs, load their profile
            viewModel.loadDogProfile(dogId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Dog Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            dogProfile?.let { dog ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                    // Display current profile info
                    SharedProfileSection(title = "Basic Information") {
                        Text(
                            text = "Current profile information is shown below. To modify these details, please complete the questionnaire again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SharedDogProfileField("Name", dog.name)
                        SharedDogProfileField("Breed", dog.breed)
                        SharedDogProfileField("Age", "${dog.age} years")
                        SharedDogProfileField("Gender", dog.gender)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onNavigateToQuestionnaire(dogId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update Profile via Questionnaire")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            } ?: run {
                // Show error state if profile couldn't be loaded
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Unable to load dog profile",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
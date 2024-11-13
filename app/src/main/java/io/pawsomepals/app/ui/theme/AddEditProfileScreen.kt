package io.pawsomepals.app.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pawsomepals.app.viewmodel.ProfileViewModel
import io.pawsomepals.app.ui.components.SharedDogProfileField
import io.pawsomepals.app.ui.components.SharedProfileSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDogProfileScreen(
    viewModel: ProfileViewModel,
    dogId: String,
    onNavigateBack: () -> Unit,
    onNavigateToQuestionnaire: (String?) -> Unit
) {
    val dogProfile by viewModel.dogProfile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = error ?: "An error occurred",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadDogProfile(dogId) }) {
                            Text("Retry")
                        }
                    }
                }
                dogProfile != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        SharedProfileSection(title = "Basic Information") {
                            Text(
                                text = "Current profile information is shown below. To modify these details, please complete the questionnaire again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            with(dogProfile!!) {
                                SharedDogProfileField("Name", name)
                                SharedDogProfileField("Breed", breed)
                                SharedDogProfileField("Age", "$age years")
                                SharedDogProfileField("Gender", gender)
                                size?.let { SharedDogProfileField("Size", it) }
                                energyLevel?.let { SharedDogProfileField("Energy Level", it) }
                                friendliness?.let { SharedDogProfileField("Friendliness", it) }
                            }
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
                }
                else -> {
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
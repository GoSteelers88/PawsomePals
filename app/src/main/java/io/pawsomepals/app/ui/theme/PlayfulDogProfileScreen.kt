package io.pawsomepals.app.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.viewmodel.ProfileViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pawsomepals.app.ui.components.DogCard
import io.pawsomepals.app.ui.components.DogDetailedProfile
import io.pawsomepals.app.ui.components.EmptyDogsView
import io.pawsomepals.app.ui.components.FloatingBonesAnimation
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayfulDogProfileScreen(
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,  // Add this parameter
    onNavigateBack: () -> Unit,
    onNavigateToAddDog: () -> Unit
) {
    val userDogs by dogProfileViewModel.userDogs.collectAsStateWithLifecycle()
    val questionnairesResponses by viewModel.questionnaireResponses.collectAsStateWithLifecycle()
    var selectedDogId by remember { mutableStateOf<String?>(null) }
    var showFloatingBones by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Background animation
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val backgroundColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.background,
        targetValue = MaterialTheme.colorScheme.surface,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backgroundColorAnimation"
    )
    LaunchedEffect(Unit) {
        dogProfileViewModel.loadUserDogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Pawsome Pals")
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = null,
                            modifier = Modifier.scale(1.2f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddDog,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(Icons.Default.Add, "Add Dog")
                Spacer(Modifier.width(8.dp))
                Text("Add New Pup!")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor)
        ) {
            if (userDogs.isEmpty()) {
                EmptyDogsView(onAddDog = onNavigateToAddDog)
            } else {
                DogProfileContent(
                    dogs = userDogs,
                    questionnairesResponses = questionnairesResponses,
                    selectedDogId = selectedDogId,
                    onDogSelected = { dogId ->
                        scope.launch {
                            selectedDogId = dogId
                            showFloatingBones = false
                        }
                    }
                )
            }

            // Floating bones animation
            if (showFloatingBones) {
                FloatingBonesAnimation()
            }
        }
    }
}

@Composable
private fun DogProfileContent(
    dogs: List<Dog>,
    questionnairesResponses: Map<String, Map<String, String>>,
    selectedDogId: String?,
    onDogSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Pack",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(dogs) { dog ->
                DogCard(
                    dog = dog,
                    responses = questionnairesResponses[dog.id] ?: emptyMap(),
                    isSelected = dog.id == selectedDogId,
                    onClick = { onDogSelected(dog.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Show detailed profile when a dog is selected
        AnimatedVisibility(
            visible = selectedDogId != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            selectedDogId?.let { dogId ->
                val selectedDog = dogs.find { it.id == dogId }
                val responses = questionnairesResponses[dogId] ?: emptyMap()
                if (selectedDog != null) {
                    DogDetailedProfile(dog = selectedDog, responses = responses)
                }
            }
        }
    }
}

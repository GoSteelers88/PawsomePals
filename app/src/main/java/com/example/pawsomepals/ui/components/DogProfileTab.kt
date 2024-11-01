package com.example.pawsomepals.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult

import android.content.pm.PackageManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pawsomepals.R
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.viewmodel.DogProfileViewModel
import com.example.pawsomepals.viewmodel.ProfileViewModel


@Composable
fun DogProfileTab(
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    onNavigateToAddDog: () -> Unit
) {
    var selectedDogId by remember { mutableStateOf<String?>(null) }
    val userDogs by dogProfileViewModel.userDogs.collectAsStateWithLifecycle()

    // Set initial selected dog if available
    LaunchedEffect(userDogs) {
        if (selectedDogId == null && userDogs.isNotEmpty()) {
            selectedDogId = userDogs.first().id
            dogProfileViewModel.setCurrentDog(userDogs.first().id)
        }
    }

    // Add this call to DogProfileList
    DogProfileList(
        dogs = userDogs,
        selectedDogId = selectedDogId,
        onDogSelected = { dogId ->
            selectedDogId = dogId
            dogProfileViewModel.setCurrentDog(dogId)
        },
        viewModel = viewModel,
        cameraLauncher = cameraLauncher,
        galleryLauncher = galleryLauncher,
        onNavigateToAddDog = onNavigateToAddDog
    )
}



@Composable
    private fun DogProfileList(
        dogs: List<Dog>,
        selectedDogId: String?,
        onDogSelected: (String) -> Unit,
        viewModel: ProfileViewModel,
        cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
        galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
        onNavigateToAddDog: () -> Unit
    ) {
        Column {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(dogs) { dog ->
                    DogSelectorItem(
                        dog = dog,
                        isSelected = dog.id == selectedDogId,
                        onClick = { onDogSelected(dog.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Only show selected dog's profile with all required parameters
            dogs.find { it.id == selectedDogId }?.let { selectedDog ->
                DogProfileContent(
                    dog = selectedDog,
                    questionnaireResponses = viewModel.questionnaireResponses
                        .collectAsStateWithLifecycle()
                        .value
                        .getOrDefault(selectedDog.id, emptyMap()),
                    cameraLauncher = cameraLauncher,  // Add this
                    galleryLauncher = galleryLauncher,  // Add this
                    viewModel = viewModel  // Add this
                )
            }
        }
    }

@Composable
private fun DogSelectorItem(dog: Dog, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        modifier = Modifier
            .size(80.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = dog.profilePictureUrl ?: R.drawable.ic_dog_placeholder,
            contentDescription = "Dog profile picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DogProfileContent(
    dog: Dog,
    questionnaireResponses: Map<String, String> = emptyMap(),
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    viewModel: ProfileViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Photo Section
        item {
            DogProfileWithPhoto(
                dog = dog,
                cameraLauncher = cameraLauncher,
                galleryLauncher = galleryLauncher,
                viewModel = viewModel
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Basic Information
        item {
            Column {
                DogBasicInfoSection(dog)
                Spacer(modifier = Modifier.height(16.dp))
                DogPersonalitySection(dog, questionnaireResponses)
            }
        }

        // Social & Behavioral Information
        item {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                DogSocialCompatibilitySection(dog, questionnaireResponses)
                Spacer(modifier = Modifier.height(16.dp))
                DogCareInfoSection(dog, questionnaireResponses)
            }
        }

        // Preferences & Additional Info
        item {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                DogPreferencesSection(dog)
                Spacer(modifier = Modifier.height(16.dp))
                DogAdditionalInfoSection(dog)
            }
        }

        // Statistics Section
        item {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        DogPersonalityTraitsStats(dog)
                        Spacer(modifier = Modifier.height(16.dp))
                        DogSocialCompatibilityStats(dog)
                        Spacer(modifier = Modifier.height(16.dp))
                        DogCareRequirementsStats(dog)
                    }
                }
            }
        }

        // Health Information
        item {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        DogHealthInformation(dog)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
@Composable
private fun DogProfileWithPhoto(
    dog: Dog,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    viewModel: ProfileViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showPhotoOptions by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isUpdatingPhoto by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }  // Add this line


    LaunchedEffect(dog.id) {
        viewModel.setCurrentDog(dog.id)
    }

    // Error dialog
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Collect error state from ViewModel
    LaunchedEffect(Unit) {
        viewModel.error.collect { error ->
            error?.let {
                errorMessage = it
                isUpdatingPhoto = false
            }
        }
    }

    val handleCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        Log.d("DogProfileWithPhoto", "Camera result success: $success, URI: $tempPhotoUri")
        if (success && tempPhotoUri != null) {
            isUpdatingPhoto = true
            coroutineScope.launch {
                try {
                    Log.d("DogProfileWithPhoto", "Starting photo update with URI: $tempPhotoUri")
                    viewModel.updateDogProfilePicture(0, tempPhotoUri!!)
                    Log.d("DogProfileWithPhoto", "Photo update completed")
                } catch(e: Exception) {
                    Log.e("DogProfileWithPhoto", "Error updating profile picture", e)
                    errorMessage = e.message ?: "Failed to update profile picture"
                } finally {
                    isUpdatingPhoto = false
                }
            }
        }
    }

    val handleGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isUpdatingPhoto = true
            coroutineScope.launch {
                try {
                    viewModel.updateDogProfilePicture(0, it)
                } catch(e: Exception) {
                    Log.e("DogProfileWithPhoto", "Error updating profile picture", e)
                    errorMessage = e.message ?: "Failed to update profile picture"
                } finally {
                    isUpdatingPhoto = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = if (dog.photoUrls.isNotEmpty()) dog.photoUrls[0] else R.drawable.ic_dog_placeholder,
                contentDescription = "Dog profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = { showPhotoOptions = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Change photo",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        if (isUpdatingPhoto) {
            CircularProgressIndicator(
                modifier = Modifier.padding(8.dp)
            )
        }

        if (showPhotoOptions) {
            AlertDialog(
                onDismissRequest = { showPhotoOptions = false },
                title = { Text("Change Profile Picture") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                Log.d("DogProfileWithPhoto", "Taking new photo")
                                tempPhotoUri = viewModel.getOutputFileUri(isProfile = false)
                                Log.d("DogProfileWithPhoto", "Created tempPhotoUri: $tempPhotoUri")
                                tempPhotoUri?.let { uri ->
                                    handleCameraLauncher.launch(uri)
                                }
                                showPhotoOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Take Photo")
                        }

                        FilledTonalButton(
                            onClick = {
                                handleGalleryLauncher.launch("image/*")
                                showPhotoOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Choose from Gallery")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPhotoOptions = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
@Composable
private fun DogBasicInfoSection(dog: Dog) {
    SharedProfileSection("Basic Info") {
        SharedDogProfileField("Name", dog.name)
        SharedDogProfileField("Breed", dog.breed)
        SharedDogProfileField("Age", "${dog.age} years")
        SharedDogProfileField("Gender", dog.gender)
        SharedDogProfileField("Size", dog.size)
    }
}

@Composable
private fun DogPersonalitySection(dog: Dog, questionnaireResponses: Map<String, String>) {
    SharedProfileSection("Personality") {
        SharedDogProfileField(
            "Energy Level",
            questionnaireResponses["energyLevel"] ?: dog.energyLevel
        )
        SharedDogProfileField(
            "Friendliness",
            questionnaireResponses["friendliness"] ?: dog.friendliness
        )
        SharedDogProfileField(
            "Trainability",
            questionnaireResponses["trainability"] ?: dog.trainability ?: "Unknown"
        )
    }
}

@Composable
private fun DogSocialCompatibilitySection(dog: Dog, questionnaireResponses: Map<String, String>) {
    SharedProfileSection("Social Compatibility") {
        SharedDogProfileField(
            "Friendly with Dogs",
            questionnaireResponses["friendlyWithDogs"] ?: dog.friendlyWithDogs ?: "Unknown"
        )
        SharedDogProfileField(
            "Friendly with Children",
            questionnaireResponses["friendlyWithChildren"] ?: dog.friendlyWithChildren ?: "Unknown"
        )
        SharedDogProfileField(
            "Friendly with Strangers",
            questionnaireResponses["friendlyWithStrangers"] ?: dog.friendlyWithStrangers ?: "Unknown"
        )
    }
}

@Composable
private fun DogCareInfoSection(dog: Dog, questionnaireResponses: Map<String, String>) {
    SharedProfileSection("Care Information") {
        SharedDogProfileField(
            "Exercise Needs",
            questionnaireResponses["exerciseNeeds"] ?: dog.exerciseNeeds ?: "Unknown"
        )
        SharedDogProfileField(
            "Grooming Needs",
            questionnaireResponses["groomingNeeds"] ?: dog.groomingNeeds ?: "Unknown"
        )
        SharedDogProfileField(
            "Special Needs",
            questionnaireResponses["specialNeeds"] ?: dog.specialNeeds ?: "None"
        )
        SharedDogProfileField(
            "Spayed/Neutered",
            questionnaireResponses["isSpayedNeutered"] ?: (dog.isSpayedNeutered ?: "Unknown")
        )
    }
}

@Composable
private fun DogPreferencesSection(dog: Dog) {
    SharedProfileSection("Preferences") {
        SharedDogProfileField("Favorite Toy", dog.favoriteToy ?: "Unknown")
        SharedDogProfileField("Favorite Treat", dog.favoriteTreat ?: "Unknown")
        SharedDogProfileField("Preferred Activities", dog.preferredActivities ?: "Unknown")
        SharedDogProfileField("Walk Frequency", dog.walkFrequency ?: "Unknown")
    }
}

@Composable
private fun DogAdditionalInfoSection(dog: Dog) {
    SharedProfileSection("Additional Info") {
        SharedDogProfileField("Weight", "${dog.weight ?: "Unknown"} kg")
        SharedDogProfileField("Training Certifications", dog.trainingCertifications ?: "None")
    }
}

// Stats Components
@Composable
private fun DogPersonalityTraitsStats(dog: Dog) {
    Text(
        "Personality Traits",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    SharedStatBar("Energy", dog.energyLevel.toSharedProgressPercentage())
    SharedStatBar("Friendliness", dog.friendliness.toSharedProgressPercentage())
    SharedStatBar("Trainability", (dog.trainability ?: "medium").toSharedProgressPercentage())
}

@Composable
private fun DogSocialCompatibilityStats(dog: Dog) {
    Text(
        "Social Compatibility",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    SharedStatBar("With Dogs", (dog.friendlyWithDogs ?: "medium").toSharedProgressPercentage())
    SharedStatBar("With Children", (dog.friendlyWithChildren ?: "medium").toSharedProgressPercentage())
    SharedStatBar("With Strangers", (dog.friendlyWithStrangers ?: "medium").toSharedProgressPercentage())
}

@Composable
private fun DogCareRequirementsStats(dog: Dog) {
    Text(
        "Care Requirements",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    SharedStatBar("Exercise Needs", (dog.exerciseNeeds ?: "medium").toSharedProgressPercentage())
    SharedStatBar("Grooming Needs", (dog.groomingNeeds ?: "medium").toSharedProgressPercentage())
}

@Composable
private fun DogHealthInformation(dog: Dog) {
    Text(
        "Health Information",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Age", style = MaterialTheme.typography.bodyLarge)
        Text(
            "${dog.age} years",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Weight", style = MaterialTheme.typography.bodyLarge)
        Text(
            "${dog.weight ?: "Unknown"} kg",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Spayed/Neutered", style = MaterialTheme.typography.bodyLarge)
        Text(
            dog.isSpayedNeutered ?: "Unknown",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
// Photo Management Components
@Composable
private fun DogGalleryContent(
    dog: Dog,
    viewModel: ProfileViewModel,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>
) {
    var showPhotoOptions by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val photoUri = viewModel.getOutputFileUri(isProfile = false) // Updated this line
            cameraLauncher.launch(photoUri)
        }
    }

    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                val photoUri = viewModel.getOutputFileUri(isProfile = false) // Updated this line
                cameraLauncher.launch(photoUri)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(dog.photoUrls) { photoUrl ->
            DogPhotoItem(photoUrl)
        }
        item {
            AddPhotoButton {
                selectedPhotoIndex = dog.photoUrls.size
                showPhotoOptions = true
            }
        }
    }

    if (showPhotoOptions) {
        PhotoOptionsDialog(
            onDismiss = { showPhotoOptions = false },
            onTakePhoto = {
                launchCamera()
                showPhotoOptions = false
            },
            onChooseFromGallery = {
                galleryLauncher.launch("image/*")
                showPhotoOptions = false
            }
        )
    }
}

@Composable
private fun DogPhotoItem(photoUrl: String?) {
    AsyncImage(
        model = photoUrl,
        contentDescription = "Dog photo",
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun AddPhotoButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add photo",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PhotoOptionsDialog(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Photo Option") },
        text = { Text("Select a method to add a photo") },
        confirmButton = {
            TextButton(onClick = onTakePhoto) {
                Text("Take Photo")
            }
        },
        dismissButton = {
            TextButton(onClick = onChooseFromGallery) {
                Text("Choose from Gallery")
            }
        }
    )
}
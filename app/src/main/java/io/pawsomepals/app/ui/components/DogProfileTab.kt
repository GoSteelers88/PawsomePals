package io.pawsomepals.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.utils.CameraPermissionHandler
import io.pawsomepals.app.utils.CameraPermissionManager
import io.pawsomepals.app.utils.CameraPermissionState
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch


@Composable
fun DogProfileTab(
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    cameraPermissionManager: CameraPermissionManager, // Add this
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
        cameraPermissionManager = cameraPermissionManager, // Add this
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
        cameraPermissionManager: CameraPermissionManager, // Add this

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
                    cameraLauncher = cameraLauncher,
                    galleryLauncher = galleryLauncher,
                    viewModel = viewModel,
                    cameraPermissionManager = cameraPermissionManager  // Add this
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
    cameraPermissionManager: CameraPermissionManager,
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
                viewModel = viewModel,
                cameraPermissionManager = cameraPermissionManager // Add this

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
    viewModel: ProfileViewModel,
    cameraPermissionManager: CameraPermissionManager  // Add this parameter

) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPhotoOptions by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isUpdatingPhoto by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Add permission handling state
    val permissionState = remember { CameraPermissionState.create(context) }
    var shouldLaunchCamera by remember { mutableStateOf(false) }
    val handleCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            isUpdatingPhoto = true
            scope.launch {  // Use the remembered scope
                try {
                    viewModel.updateDogProfilePicture(0, tempPhotoUri!!)
                } catch(e: Exception) {
                    errorMessage = e.message ?: "Failed to update profile picture"
                } finally {
                    isUpdatingPhoto = false
                    tempPhotoUri = null  // Clear the URI after processing
                }
            }
        }
    }

    val handleGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isUpdatingPhoto = true
            scope.launch {  // Use scope instead of coroutineScope
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

    LaunchedEffect(dog.id) {
        viewModel.setCurrentDog(dog.id)
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

    // Function to handle camera launch after permission
    fun handleCameraLaunch() {
        scope.launch {
            try {
                tempPhotoUri = viewModel.getOutputFileUri(isProfile = true)
                tempPhotoUri?.let { uri ->
                    cameraLauncher.launch(uri)
                }
            } catch (e: SecurityException) {
                errorMessage = "Failed to launch camera: ${e.message}"
            }
        }
    }

    // Permission handler component
    CameraPermissionHandler(
        viewModel = cameraPermissionManager,
        onPermissionGranted = {
            if (shouldLaunchCamera) {
                handleCameraLaunch()
                shouldLaunchCamera = false
            }
        },
        onPermissionDenied = {
            errorMessage = "Camera permission is required to take photos"
            shouldLaunchCamera = false
        }
    )

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
                                shouldLaunchCamera = true
                                showPhotoOptions = false
                                if (permissionState.permissionState.value is CameraPermissionState.PermissionState.Granted) {
                                    handleCameraLaunch()
                                }
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
    }

    // Handle photo update in a LaunchedEffect
    LaunchedEffect(isUpdatingPhoto) {
        if (isUpdatingPhoto && tempPhotoUri != null) {
            try {
                viewModel.updateUserProfilePicture(tempPhotoUri!!)
            } catch (e: Exception) {
                errorMessage = "Failed to update profile picture: ${e.message}"
            } finally {
                isUpdatingPhoto = false
                tempPhotoUri = null
            }
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
    val scope = rememberCoroutineScope()
    var showPhotoOptions by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    var shouldLaunchCamera by remember { mutableStateOf(false) } // Add this
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
                            shouldLaunchCamera = true
                            showPhotoOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Take Photo")
                    }

                    FilledTonalButton(
                        onClick = {
                            galleryLauncher.launch("image/*")
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
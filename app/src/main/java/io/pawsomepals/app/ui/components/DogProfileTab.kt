package io.pawsomepals.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.utils.CameraManager
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch


@Composable
fun DogProfileTab(
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    cameraManager: CameraManager,
    onNavigateToAddDog: () -> Unit
) {
    var selectedDogId by remember { mutableStateOf<String?>(null) }
    val userDogs by dogProfileViewModel.userDogs.collectAsStateWithLifecycle()
    val dogProfileState by dogProfileViewModel.dogProfileState.collectAsState()

    // Add debug logs
    LaunchedEffect(Unit) {
        Log.d("DogProfileTab", "DogProfileTab initialized")
        dogProfileViewModel.loadUserDogs() // Always try to load dogs when tab is shown
    }

    when (dogProfileState) {
        is DogProfileViewModel.DogProfileState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is DogProfileViewModel.DogProfileState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (dogProfileState as DogProfileViewModel.DogProfileState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        else -> {
            if (userDogs.isEmpty()) {
                // Show empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No dogs found",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(onClick = onNavigateToAddDog) {
                        Text("Add Your First Dog")
                    }
                }
            } else {
                // Initialize dog selection once
                LaunchedEffect(userDogs) {
                    if (selectedDogId == null && userDogs.isNotEmpty()) {
                        val firstDogId = userDogs.first().id
                        Log.d("DogProfileTab", "Setting initial dog ID: $firstDogId")
                        selectedDogId = firstDogId
                        dogProfileViewModel.setCurrentDog(firstDogId)
                    }
                }

                DogProfileList(
                    dogs = userDogs,
                    selectedDogId = selectedDogId,
                    onDogSelected = { dogId ->
                        Log.d("DogProfileTab", "Dog selected: $dogId")
                        selectedDogId = dogId
                        dogProfileViewModel.setCurrentDog(dogId)
                    },
                    viewModel = viewModel,
                    dogProfileViewModel = dogProfileViewModel,
                    galleryLauncher = galleryLauncher,
                    cameraManager = cameraManager,
                    onNavigateToAddDog = onNavigateToAddDog
                )
            }
        }
    }
}

@Composable
private fun DogProfileList(
    dogs: List<Dog>,
    selectedDogId: String?,
    onDogSelected: (String) -> Unit,
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    cameraManager: CameraManager,
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

            dogs.find { it.id == selectedDogId }?.let { selectedDog ->
                DogProfileContent(
                    dog = selectedDog,
                    questionnaireResponses = viewModel.questionnaireResponses
                        .collectAsStateWithLifecycle()
                        .value
                        .getOrDefault(selectedDog.id, emptyMap()),
                    galleryLauncher = galleryLauncher,
                    viewModel = viewModel,
                    dogProfileViewModel = dogProfileViewModel,  // Pass this through
                    cameraManager = cameraManager  // Updated parameter name
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
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    cameraManager: CameraManager,
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel
){
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Photo Section
        item {
            DogProfileWithPhoto(
                dog = dog,
                galleryLauncher = galleryLauncher,
                viewModel = viewModel,
                dogProfileViewModel = dogProfileViewModel,  // Pass this through
                cameraManager = cameraManager,

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
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    cameraManager: CameraManager
) {
    var showCamera by remember { mutableStateOf(false) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraState by cameraManager.cameraState.collectAsStateWithLifecycle()


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            showCamera = true
        } else {
            errorMessage = "Camera and storage permissions are required"
        }
    }
    LaunchedEffect(cameraState) {
        when (cameraState) {
            is CameraManager.CameraState.Success -> {
                val uri = (cameraState as CameraManager.CameraState.Success).uri
                dogProfileViewModel.updateDogProfilePicture(0, uri, dog.id)
                showCamera = false
            }
            is CameraManager.CameraState.Error -> {
                errorMessage = (cameraState as CameraManager.CameraState.Error).message
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(160.dp).clip(CircleShape)
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .size(40.dp)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Change photo",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        if (showCamera) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        PreviewView(context).apply {
                            this.scaleType = PreviewView.ScaleType.FILL_CENTER  // FILL_CENTER will fill the entire view
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()  // Fill maximum available space
                        .fillMaxWidth() // Ensure width is filled
                        .fillMaxHeight(), // Ensure height is filled
                    update = { previewView ->
                        scope.launch {
                            cameraManager.initializeCamera(lifecycleOwner, previewView)
                        }
                    }
                )

                IconButton(
                    onClick = {
                        scope.launch {
                            val uri = cameraManager.capturePhoto()
                            uri?.let {
                                showCamera = false
                                dogProfileViewModel.updateDogProfilePicture(0, it, dog.id)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Take Photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }

        if (showPhotoOptions) {
            AlertDialog(
                onDismissRequest = { showPhotoOptions = false },
                title = { Text("Change Dog's Picture") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                when {
                                    cameraManager.hasRequiredPermissions() -> {
                                        showCamera = true
                                    }
                                    else -> {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                                            )
                                        )
                                    }
                                }
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

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Error") },
                text = { Text(errorMessage ?: "") },
                confirmButton = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.cleanup()
        }
    }
}
private fun shouldShowRequestPermissionRationale(context: Context, permission: String): Boolean {
    val activity = context.findActivity()
    return activity?.shouldShowRequestPermissionRationale(permission) ?: false
}
private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
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
            if (dog.isSpayedNeutered == true) "Yes" else "No"
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
            if (dog.isSpayedNeutered == true) "Yes" else "No",
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
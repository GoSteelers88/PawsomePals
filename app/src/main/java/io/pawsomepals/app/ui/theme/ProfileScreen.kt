package io.pawsomepals.app.ui.theme

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.ui.components.DirectionalIcon
import io.pawsomepals.app.ui.components.DogProfileTab
import io.pawsomepals.app.ui.components.SharedProfileField
import io.pawsomepals.app.ui.components.SharedProfileSection
import io.pawsomepals.app.utils.CameraPermissionHandler
import io.pawsomepals.app.utils.CameraPermissionManager
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    userId: String,
    cameraPermissionManager: CameraPermissionManager,
    onBackClick: () -> Unit,
    onNavigateToAddDog: () -> Unit

) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("User Profile", "Dog Profile")
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isUpdatingPhoto by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val user = viewModel.userProfile.collectAsState().value

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            isUpdatingPhoto = true
            viewModel.updateUserProfilePicture(tempPhotoUri!!)
            tempPhotoUri = null
            isUpdatingPhoto = false
        }
    }


    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.updateUserProfilePicture(it) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                tempPhotoUri = viewModel.getOutputFileUri(isProfile = true)  // Updated this line
                tempPhotoUri?.let { uri ->
                    cameraLauncher.launch(uri)
                    isUpdatingPhoto = true
                }
            } catch (e: Exception) {
                errorMessage = "Failed to initialize camera: ${e.message}"
            }
        } else {
            errorMessage = "Camera permission is required to take a photo."
        }
    }


    LaunchedEffect(userId) {
        viewModel.loadProfileById(userId)
        viewModel.fetchUserDogs()
        dogProfileViewModel.loadUserDogs()  // Add this line to load dog profiles
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        DirectionalIcon(contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }

            when (selectedTab) {
                0 -> UserProfileTab(
                    viewModel = viewModel,
                    cameraPermissionManager = cameraPermissionManager, // Pass this
                    cameraLauncher = cameraLauncher,
                    galleryLauncher = galleryLauncher
                )
                1 -> DogProfileTab(
                    viewModel = viewModel,
                    dogProfileViewModel = dogProfileViewModel,  // Pass the dogProfileViewModel
                    cameraLauncher = cameraLauncher,
                    galleryLauncher = galleryLauncher,
                    cameraPermissionManager = cameraPermissionManager, // Add this

                    onNavigateToAddDog = onNavigateToAddDog
                )
            }
        }
    }
}

@Composable
private fun UserPhotoSection(
    userProfile: User?,
    viewModel: ProfileViewModel,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    cameraPermissionManager: CameraPermissionManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPhotoOptions by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isUpdatingPhoto by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shouldLaunchCamera by remember { mutableStateOf(false) }

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

    // Handle camera result
    LaunchedEffect(cameraLauncher) {
        snapshotFlow { isUpdatingPhoto }
            .filter { it }
            .collect {
                tempPhotoUri?.let { uri ->
                    try {
                        viewModel.updateUserProfilePicture(uri)
                    } catch (e: Exception) {
                        errorMessage = "Failed to update profile picture: ${e.message}"
                    } finally {
                        isUpdatingPhoto = false
                        tempPhotoUri = null
                    }
                }
            }
    }

    // Camera permission handler
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

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        UserProfilePhoto(
            photoUrl = userProfile?.profilePictureUrl,
            onPhotoClick = { showPhotoOptions = true }
        )
    }

    // Photo options dialog
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
                            if (cameraPermissionManager.checkPermission()) {
                                handleCameraLaunch()
                            }
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

    // Loading Dialog
    if (isUpdatingPhoto) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Updating Profile Picture") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = { }
        )
    }

    // Error Dialog
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

@Composable
fun UserProfileTab(
    viewModel: ProfileViewModel,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>,
    cameraPermissionManager: CameraPermissionManager
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Photo Section
        item {
            UserPhotoSection(
                userProfile = userProfile,
                viewModel = viewModel,
                cameraLauncher = cameraLauncher,
                galleryLauncher = galleryLauncher,
                cameraPermissionManager = cameraPermissionManager
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // User Profile Sections
        userProfile?.let { user ->
            item { UserInfoSection(user) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { UserBioSection(user) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { UserPreferencesSection(user) }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Edit Profile Button
        item {
            Button(
                onClick = { showEditDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Profile")
            }
        }
    }

    // Edit Profile Dialog
    if (showEditDialog && userProfile != null) {
        EditProfileDialog(
            user = userProfile!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedUser ->
                viewModel.updateUserProfile(updatedUser)
                showEditDialog = false
            }
        )
    }
}
// Updated UserProfilePhoto composable
@Composable
private fun UserProfilePhoto(
    photoUrl: String?,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(CircleShape)
            .clickable(onClick = onPhotoClick)
    ) {
        AsyncImage(
            model = photoUrl ?: R.drawable.ic_user_placeholder,
            contentDescription = "User Photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit Photo",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}
@Composable
fun EditProfileDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (User) -> Unit
) {
    var firstName by remember { mutableStateOf(user.firstName ?: "") }
    var lastName by remember { mutableStateOf(user.lastName ?: "") }
    var bio by remember { mutableStateOf(user.bio ?: "") }
    var phoneNumber by remember { mutableStateOf(user.phoneNumber ?: "") }
    var preferredContact by remember { mutableStateOf(user.preferredContact ?: "") }
    var notificationsEnabled by remember { mutableStateOf(user.notificationsEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = preferredContact,
                    onValueChange = { preferredContact = it },
                    label = { Text("Preferred Contact Method") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Notifications")
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedUser = user.copy(
                        firstName = firstName,
                        lastName = lastName,
                        bio = bio,
                        phoneNumber = phoneNumber,
                        preferredContact = preferredContact,
                        notificationsEnabled = notificationsEnabled
                    )
                    onSave(updatedUser)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}




@Composable
private fun UserInfoSection(user: User) {
    SharedProfileSection("Personal Information") {
        SharedProfileField("Name", "${user.firstName} ${user.lastName}")
        SharedProfileField("Username", user.username)
        SharedProfileField("Email", user.email)
        SharedProfileField("Phone", user.phoneNumber ?: "Not provided")
    }
}

@Composable
private fun UserBioSection(user: User) {
    SharedProfileSection("About Me") {
        Text(
            text = user.bio ?: "No bio provided",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun UserPreferencesSection(user: User) {
    SharedProfileSection("Preferences") {
        SharedProfileField("Preferred Contact", user.preferredContact ?: "Not specified")
        SharedProfileField("Notification Settings", if (user.notificationsEnabled == true) "Enabled" else "Disabled")
    }
}
@Composable
private fun UserPhotoOptionsDialog( // Renamed from PhotoOptionsDialog
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

@Composable
private fun LoadingDialog() {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Updating Profile Picture") },
        text = { CircularProgressIndicator() },
        confirmButton = { }
    )
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
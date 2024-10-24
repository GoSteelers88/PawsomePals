package com.example.pawsomepals.ui.theme

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pawsomepals.R
import com.example.pawsomepals.data.model.User
import com.example.pawsomepals.ui.components.*
import com.example.pawsomepals.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    userId: String,
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


    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoUri != null) {
            viewModel.updateUserProfilePicture(tempPhotoUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateUserProfilePicture(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                tempPhotoUri = viewModel.getOutputFileUri(context)
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
                0 -> UserProfileTab(viewModel, cameraLauncher, galleryLauncher)
                1 -> DogProfileTab(
                    viewModel = viewModel,
                    cameraLauncher = cameraLauncher,
                    galleryLauncher = galleryLauncher,
                    onNavigateToAddDog = onNavigateToAddDog
                )
            }
        }
    }
}


@Composable
fun UserProfileTab(
    viewModel: ProfileViewModel,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var showPhotoOptions by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isUpdatingPhoto by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempPhotoUri = viewModel.getOutputFileUri(context)
            tempPhotoUri?.let { uri -> cameraLauncher.launch(uri) }
        } else {
            errorMessage = "Camera permission is required to take a photo."
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            UserProfilePhoto(
                photoUrl = userProfile?.profilePictureUrl,
                onPhotoClick = { showPhotoOptions = true }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        userProfile?.let { user ->
            item { UserInfoSection(user) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { UserBioSection(user) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { UserPreferencesSection(user) }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Button(
                onClick = { showEditDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Profile")
            }
        }
    }

    // Add the EditProfileDialog
    if (showEditDialog && userProfile != null) {
        EditProfileDialog(
            user = userProfile!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedUser ->
                viewModel.updateUserProfile(updatedUser)
            }
        )
    }

    if (showPhotoOptions) {
        UserPhotoOptionsDialog(
            onDismiss = { showPhotoOptions = false },
            onTakePhoto = {
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                        try {
                            tempPhotoUri = viewModel.getOutputFileUri(context)
                            tempPhotoUri?.let { uri ->
                                cameraLauncher.launch(uri)
                                isUpdatingPhoto = true
                            }
                        } catch (e: Exception) {
                            errorMessage = "Failed to initialize camera: ${e.message}"
                        }
                    }
                    else -> permissionLauncher.launch(Manifest.permission.CAMERA)
                }
                showPhotoOptions = false
            },
            onChooseFromGallery = {
                galleryLauncher.launch("image/*")
                showPhotoOptions = false
            }
        )
    }
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

    // Show loading indicator while updating photo
    if (isUpdatingPhoto) {
        LoadingDialog()
    }

    // Show error message if any
    errorMessage?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = { errorMessage = null }
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
private fun UserProfilePhoto(photoUrl: String?, onPhotoClick: () -> Unit) {
    Box(
        modifier = Modifier
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
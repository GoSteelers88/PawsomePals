package io.pawsomepals.app.ui.theme


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.ui.components.DirectionalIcon
import io.pawsomepals.app.ui.components.DogProfileTab
import io.pawsomepals.app.utils.CameraManager
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    userId: String,
    onBackClick: () -> Unit,
    onNavigateToAddDog: () -> Unit,
    cameraManager: CameraManager
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("User Profile", "Dog Profile")
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.updateUserProfilePicture(it) } }



    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            viewModel.loadProfileById(userId)
            viewModel.fetchUserDogs()
            dogProfileViewModel.loadUserDogs()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.cleanup()
        }
    }

    error?.let { errorMessage ->
        ErrorDialog(
            message = errorMessage,
            onDismiss = { viewModel.setError(null) }
        )
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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
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
                    0 -> UserProfileContent(
                        userProfile = userProfile,
                        viewModel = viewModel,
                        cameraManager = cameraManager,
                        galleryLauncher = galleryLauncher
                        // Remove cameraLauncher parameter
                    )
                    1 -> DogProfileTab(
                        viewModel = viewModel,
                        dogProfileViewModel = dogProfileViewModel,
                        onNavigateToAddDog = onNavigateToAddDog,
                        cameraManager = cameraManager,
                        galleryLauncher = galleryLauncher

                    )
                }
            }
        }
    }
}

@Composable
private fun UserProfileContent(
    userProfile: User?,
    viewModel: ProfileViewModel,
    cameraManager: CameraManager,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>
) {
    var isEditing by remember { mutableStateOf(false) }
    var username by remember(userProfile) { mutableStateOf(userProfile?.username ?: "") }
    var firstName by remember(userProfile) { mutableStateOf(userProfile?.firstName ?: "") }
    var lastName by remember(userProfile) { mutableStateOf(userProfile?.lastName ?: "") }
    var bio by remember(userProfile) { mutableStateOf(userProfile?.bio ?: "") }
    var phoneNumber by remember(userProfile) { mutableStateOf(userProfile?.phoneNumber ?: "") }
    var preferredContact by remember(userProfile) { mutableStateOf(userProfile?.preferredContact ?: "") }
    var notificationsEnabled by remember(userProfile) { mutableStateOf(userProfile?.notificationsEnabled ?: false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PhotoSection(
                photoUrl = userProfile?.profilePictureUrl,
                userId = userProfile?.id ?: "",
                onPhotoUpdate = { uri -> viewModel.updateUserProfilePicture(uri) },
                cameraManager = cameraManager,
                galleryLauncher = galleryLauncher
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileTextField(
                    value = username,
                    onValueChange = { newUsername ->
                        // Optional: Add username validation here
                        if (newUsername.length <= 30 && !newUsername.contains(" ")) {
                            username = newUsername
                        }
                    },
                    label = "Username",
                    enabled = isEditing,
                    helperText = if (isEditing) "Username must be unique and without spaces" else null
                )
                ProfileTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = "First Name",
                    enabled = isEditing
                )
                ProfileTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = "Last Name",
                    enabled = isEditing
                )
                ProfileTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = "Bio",
                    enabled = isEditing,
                    singleLine = false,
                    minLines = 3
                )
                ProfileTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = "Phone Number",
                    enabled = isEditing
                )
                ProfileTextField(
                    value = preferredContact,
                    onValueChange = { preferredContact = it },
                    label = "Preferred Contact Method",
                    enabled = isEditing
                )

                if (isEditing) {
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
            }
        }

        item {
            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            userProfile?.let { user ->
                                viewModel.updateUserProfile(
                                    user.copy(
                                        username = username,  // Add username to update
                                        firstName = firstName,
                                        lastName = lastName,
                                        bio = bio,
                                        phoneNumber = phoneNumber,
                                        preferredContact = preferredContact,
                                        notificationsEnabled = notificationsEnabled
                                    )
                                )
                            }
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                    Button(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
            } else {
                Button(
                    onClick = { isEditing = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Profile")
                }
            }
        }
    }
}
@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    helperText: String? = null
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines
        )
        helperText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun PhotoSection(
    photoUrl: String?,
    userId: String,
    onPhotoUpdate: (Uri) -> Unit,
    cameraManager: CameraManager,
    galleryLauncher: ManagedActivityResultLauncher<String, Uri?>
) {
    var showCamera by remember { mutableStateOf(false) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraState by cameraManager.cameraState.collectAsStateWithLifecycle()

    LaunchedEffect(cameraState) {
        when (cameraState) {
            is CameraManager.CameraState.Success -> {
                val uri = (cameraState as CameraManager.CameraState.Success).uri
                onPhotoUpdate(uri)
                showCamera = false
            }
            is CameraManager.CameraState.Error -> {
                errorMessage = (cameraState as CameraManager.CameraState.Error).message
            }
            else -> {}
        }
    }

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

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (showCamera) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
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
                                onPhotoUpdate(it)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Take Photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        } else {
            AsyncImage(
                model = photoUrl ?: R.drawable.ic_user_placeholder,
                contentDescription = "Profile Photo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable { showPhotoOptions = true },
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
    DisposableEffect(Unit) {
        onDispose {
            if (showCamera) {
                cameraManager.cleanup()
            }
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
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines
    )
}

@Composable
private fun ErrorDialog(
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
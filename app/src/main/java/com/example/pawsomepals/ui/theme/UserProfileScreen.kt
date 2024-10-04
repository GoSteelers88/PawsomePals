package com.example.pawsomepals.ui.theme

import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.pawsomepals.R
import com.example.pawsomepals.data.model.User
import com.example.pawsomepals.ui.components.DirectionalIcon
import com.example.pawsomepals.viewmodel.ProfileViewModel
import com.google.firebase.storage.FirebaseStorage
import utils.rememberCameraLauncher

private val storage = FirebaseStorage.getInstance()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editField by remember { mutableStateOf("") }
    var editValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberCameraLauncher { uri ->
        if (uri != Uri.EMPTY) {
            photoUri = uri
            viewModel.updateUserProfilePicture(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            userProfile?.let { user ->
                ProfileImage(user, photoUri, viewModel, context, cameraLauncher)
                Spacer(modifier = Modifier.height(16.dp))
                ProfileDetails(user, onEditClick = { field, value ->
                    showEditDialog = true
                    editField = field
                    editValue = value
                })
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditDialog(
            field = editField,
            initialValue = editValue,
            onDismiss = { showEditDialog = false },
            onSave = { newValue ->
                when (editField) {
                    "Username" -> {
                        if (newValue.length < 3) {
                            errorMessage = "Username must be at least 3 characters long"
                        } else {
                            viewModel.updateUserProfile(mapOf("username" to newValue))
                            showEditDialog = false
                        }
                    }
                    "Email" -> {
                        if (!Patterns.EMAIL_ADDRESS.matcher(newValue).matches()) {
                            errorMessage = "Invalid email format"
                        } else {
                            viewModel.updateUserProfile(mapOf("email" to newValue))
                            showEditDialog = false
                        }
                    }
                    "Name" -> {
                        val names = newValue.split(" ")
                        if (names.size < 2) {
                            errorMessage = "Please provide both first and last name"
                        } else {
                            viewModel.updateUserProfile(mapOf(
                                "firstName" to names[0],
                                "lastName" to names.subList(1, names.size).joinToString(" ")
                            ))
                            showEditDialog = false
                        }
                    }
                    "Bio" -> {
                        viewModel.updateUserProfile(mapOf("bio" to newValue))
                        showEditDialog = false
                    }
                }
                errorMessage = null  // Clear error message after successful update
            }
        )
    }
}



@Composable
fun ProfileImage(
    user: User,
    photoUri: Uri?,
    viewModel: ProfileViewModel,
    context: android.content.Context,
    cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>
) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .padding(8.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = photoUri ?: user.profilePictureUrl ?: R.drawable.ic_user_placeholder
            ),
            contentDescription = "User Picture",
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        FloatingActionButton(
            onClick = {
                val uri = viewModel.getOutputFileUri(context)
                cameraLauncher.launch(uri)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(40.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_camera),
                contentDescription = "Take Photo"
            )
        }
    }
}

@Composable
fun ProfileDetails(user: User, onEditClick: (String, String) -> Unit) {
    ProfileItemWithEdit("Username", user.username) { onEditClick("Username", user.username) }
    ProfileItemWithEdit("Email", user.email) { onEditClick("Email", user.email) }
    ProfileItemWithEdit("Name", "${user.firstName} ${user.lastName}") { onEditClick("Name", "${user.firstName} ${user.lastName}") }
    ProfileItemWithEdit("Bio", user.bio ?: "No bio provided") { onEditClick("Bio", user.bio ?: "") }
}

@Composable
fun ProfileItemWithEdit(label: String, value: String, onEditClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "Edit $label")
        }
    }
    Divider()
}

@Composable
fun EditDialog(
    field: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var editValue by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Edit $field",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = { Text(field) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(editValue) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
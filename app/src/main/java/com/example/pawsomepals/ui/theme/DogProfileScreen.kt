package com.example.pawsomepals.ui.theme

import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.pawsomepals.R
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.ui.components.DirectionalIcon
import com.example.pawsomepals.viewmodel.DogProfileViewModel
import utils.rememberCameraLauncher
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DogProfileScreen(
    viewModel: DogProfileViewModel,
    onBackClick: () -> Unit
) {
    val dogProfile by viewModel.dogProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editField by remember { mutableStateOf("") }
    var editValue by remember { mutableStateOf("") }

    val cameraLauncher = rememberCameraLauncher { uri ->
        if (uri != Uri.EMPTY) {
            photoUri = uri
            viewModel.updateDogProfilePicture(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dog Profile") },
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
            when (val profile = dogProfile) {
                null -> {
                    if (viewModel.userProfile.collectAsStateWithLifecycle().value != null) {
                        Text("No dog profile found. Please create a dog profile.")
                    } else {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    DogProfileContent(
                        dog = profile,
                        photoUri = photoUri,
                        viewModel = viewModel,
                        context = context,
                        cameraLauncher = cameraLauncher,
                        onEditClick = { field, value ->
                            showEditDialog = true
                            editField = field
                            editValue = value
                        }
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditDogDialog(
            field = editField,
            initialValue = editValue,
            onDismiss = { showEditDialog = false },
            onSave = { newValue ->
                viewModel.updateDogProfile(editField, newValue)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun DogProfileContent(
    dog: DogProfile,
    photoUri: Uri?,
    viewModel: DogProfileViewModel,
    context: android.content.Context,
    cameraLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Uri, Boolean>,
    onEditClick: (String, String) -> Unit
) {
    DogProfileImage(dog, photoUri, viewModel, context, cameraLauncher)
    Spacer(modifier = Modifier.height(16.dp))
    DogProfileDetails(dog, onEditClick)
}

@Composable
fun DogProfileImage(
    dog: DogProfile,
    photoUri: Uri?,
    viewModel: DogProfileViewModel,
    context: android.content.Context,
    cameraLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Uri, Boolean>
) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .padding(8.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = photoUri ?: dog.profilePictureUrl ?: R.drawable.ic_dog_placeholder
            ),
            contentDescription = "Dog Picture",
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
fun DogProfileDetails(dog: DogProfile, onEditClick: (String, String) -> Unit) {
    val details = listOf(
        "Name" to dog.name,
        "Breed" to dog.breed,
        "Age" to dog.age.toString(),
        "Gender" to dog.gender,
        "Size" to dog.size,
        "Energy Level" to dog.energyLevel,
        "Friendliness" to dog.friendliness,
        "Spayed/Neutered" to (dog.isSpayedNeutered?.let { if (it) "Yes" else "No" } ?: "Not specified"),
        "Friendly with dogs" to (dog.friendlyWithDogs ?: "Not specified"),
        "Friendly with children" to (dog.friendlyWithChildren ?: "Not specified"),
        "Special needs" to (dog.specialNeeds ?: "None"),
        "Favorite toy" to (dog.favoriteToy ?: "Not specified"),
        "Preferred activities" to (dog.preferredActivities ?: "Not specified"),
        "Walk frequency" to (dog.walkFrequency ?: "Not specified"),
        "Favorite treat" to (dog.favoriteTreat ?: "Not specified"),
        "Training certifications" to (dog.trainingCertifications ?: "None")
    )

    details.forEach { (label, value) ->
        DogProfileItem(label, value, onEditClick)
    }
}

@Composable
fun DogProfileItem(label: String, value: String, onEditClick: (String, String) -> Unit) {
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
        IconButton(onClick = { onEditClick(label, value) }) {
            Icon(Icons.Default.Edit, contentDescription = "Edit $label")
        }
    }
    Divider()
}

@Composable
fun EditDogDialog(
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
                if (field == "Spayed/Neutered") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Is your dog spayed/neutered?")
                        Switch(
                            checked = editValue.lowercase(Locale.ROOT) == "yes",
                            onCheckedChange = { editValue = if (it) "Yes" else "No" }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text(field) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
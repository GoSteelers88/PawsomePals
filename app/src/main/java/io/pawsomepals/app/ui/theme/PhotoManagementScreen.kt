package io.pawsomepals.app.ui.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.pawsomepals.app.viewmodel.PhotoManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoManagementScreen(
    viewModel: PhotoManagementViewModel,
    onNavigateBack: () -> Unit
) {
    val photoState by viewModel.photoState.collectAsState()
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPhotoUri = it
            viewModel.uploadPhoto(it, true) // Assuming it's a user photo, adjust as needed
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Photo Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch("image/*") }) {
                Icon(Icons.Default.Add, contentDescription = "Upload Photo")
            }
        }
    ) { innerPadding ->
        when (val state = photoState) {
            is PhotoManagementViewModel.PhotoState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PhotoManagementViewModel.PhotoState.Success -> {
                // You might want to fetch and display the photos here
                Text(state.message)
            }
            is PhotoManagementViewModel.PhotoState.Error -> {
                Text("Error: ${state.message}")
            }
            else -> {
                // Idle state or display existing photos
            }
        }
    }
}
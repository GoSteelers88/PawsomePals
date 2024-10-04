package com.example.pawsomepals.ui.theme


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pawsomepals.ui.components.DirectionalIcon
import com.example.pawsomepals.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onBackClick: () -> Unit) {
    val userProfile by viewModel.userProfile.collectAsState()
    val dogProfile by viewModel.dogProfile.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Profile") },
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
                .padding(16.dp)
        ) {
            Text("User Profile", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            userProfile?.let { user ->
                Text("Username: ${user.username}")
                Text("Email: ${user.email}")
                Text("Name: ${user.firstName} ${user.lastName}")
                Text("Bio: ${user.bio ?: "No bio provided"}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Dog Profile", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            dogProfile?.let { dog ->
                Text("Name: ${dog.name}")
                Text("Breed: ${dog.breed}")
                Text("Age: ${dog.age}")
                Text("Gender: ${dog.gender}")
                Text("Size: ${dog.size}")
                Text("Energy Level: ${dog.energyLevel}")
                Text("Friendliness: ${dog.friendliness}")
            }
        }
    }
}


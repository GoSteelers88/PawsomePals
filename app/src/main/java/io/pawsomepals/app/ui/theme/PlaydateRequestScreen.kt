package io.pawsomepals.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import io.pawsomepals.app.data.model.PlaydateRequest
import io.pawsomepals.app.viewmodel.PlaydateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaydateRequestsScreen(
    viewModel: PlaydateViewModel,
    onBackClick: () -> Unit
) {
    val playdateRequests by viewModel.playdateRequests.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playdate Requests") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(playdateRequests) { request ->
                PlaydateRequestItem(
                    request = request,
                    onAccept = { viewModel.acceptPlaydateRequest(request.id) },
                    onDecline = { viewModel.declinePlaydateRequest(request.id) }
                )
            }
        }
    }
}

@Composable
fun PlaydateRequestItem(
    request: PlaydateRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("From: ${request.requesterId}", style = MaterialTheme.typography.bodyLarge)
            Text("Status: ${request.status}", style = MaterialTheme.typography.bodyMedium)
            Text("Suggested timeslots: ${request.suggestedTimeslots.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onAccept) {
                    Text("Accept")
                }
                Button(onClick = onDecline) {
                    Text("Decline")
                }
            }
        }
    }
}
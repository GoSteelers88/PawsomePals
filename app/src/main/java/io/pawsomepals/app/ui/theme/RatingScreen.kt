package io.pawsomepals.app.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.pawsomepals.app.viewmodel.RatingViewModel
import io.pawsomepals.app.data.model.Rating
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    ratingId: String,
    viewModel: RatingViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val ratingState by viewModel.ratingState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ratingId) {
        viewModel.getUserRatings(ratingId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rate Your Experience") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (ratingState) {
                is RatingViewModel.RatingState.Loading -> {
                    CircularProgressIndicator()
                }
                is RatingViewModel.RatingState.Error -> {
                    Text("Error: ${(ratingState as RatingViewModel.RatingState.Error).message}")
                }
                is RatingViewModel.RatingState.RatingsLoaded -> {
                    val ratings = (ratingState as RatingViewModel.RatingState.RatingsLoaded).ratings
                    // Display existing ratings if any
                    // For now, we'll just show a new rating form
                    RatingForm(viewModel, onNavigateBack)
                }
                else -> {
                    RatingForm(viewModel, onNavigateBack)
                }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }
}

@Composable
fun RatingForm(viewModel: RatingViewModel, onNavigateBack: () -> Unit) {
    var score by remember { mutableFloatStateOf(0f) }
    var comment by remember { mutableStateOf("") }

    val currentUserId = viewModel.getCurrentUserId() ?: return // If userId is null, we can't proceed

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "How was your experience?",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            for (i in 1..5) {
                IconButton(onClick = { score = i.toFloat() }) {
                    Icon(
                        imageVector = if (i <= score.toInt()) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Star $i",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Your Comment") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val newRating = Rating(
                    id = UUID.randomUUID().toString(),
                    userId = currentUserId, // Assuming this is the ID of the user being rated
                    raterId = currentUserId, // The current user is giving the rating
                    score = score,
                    comment = comment.takeIf { it.isNotBlank() },
                    timestamp = System.currentTimeMillis(),
                    playdateId = null // We're not setting a specific playdate ID here
                )
                viewModel.submitRating(newRating)
                onNavigateBack()
            },
            enabled = score > 0
        ) {
            Text("Submit Rating")
        }
    }
}
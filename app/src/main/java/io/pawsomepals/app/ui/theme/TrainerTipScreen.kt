package io.pawsomepals.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.pawsomepals.app.viewmodel.TrainerTipsViewModel

@Composable
fun TrainerTipsScreen(
    viewModel: TrainerTipsViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val dailyTip by viewModel.dailyTip.collectAsState()
    val trainerAdvice by viewModel.trainerAdvice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var question by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadDailyTip()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Daily Training Tip", style = MaterialTheme.typography.headlineSmall)
        Text(dailyTip)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Ask a Training Question", style = MaterialTheme.typography.headlineSmall)
        TextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Your question") }
        )
        Button(
            onClick = {
                viewModel.askTrainerQuestion(question)
                question = ""
            },
            enabled = !isLoading
        ) {
            Text("Ask")
        }

        if (trainerAdvice.isNotEmpty()) {
            Text("Trainer's Advice:", style = MaterialTheme.typography.headlineSmall)
            Text(trainerAdvice)
        }

        if (isLoading) {
            CircularProgressIndicator()
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackClick) {
            Text("Back")
        }
    }
}
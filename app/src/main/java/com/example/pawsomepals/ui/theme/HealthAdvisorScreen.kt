package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pawsomepals.subscription.SubscriptionStatus
import com.example.pawsomepals.viewmodel.HealthAdvisorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthAdvisorScreen(
    viewModel: HealthAdvisorViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val healthAdvice by viewModel.healthAdvice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pastQuestions by viewModel.pastQuestions.collectAsState()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsState()
    val remainingQuestions by viewModel.remainingQuestions.collectAsState()
    var question by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dog Health Advisor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            SubscriptionStatusBar(subscriptionStatus, remainingQuestions)

            Spacer(modifier = Modifier.height(16.dp))

            QuestionInput(
                question = question,
                onQuestionChange = { question = it },
                onAskQuestion = {
                    viewModel.askHealthQuestion(question)
                    question = ""
                },
                isLoading = isLoading,
                isEnabled = subscriptionStatus == SubscriptionStatus.ACTIVE || remainingQuestions > 0
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            healthAdvice?.let { advice ->
                HealthAdviceCard(advice)
            }

            Spacer(modifier = Modifier.height(24.dp))

            PastQuestionsList(pastQuestions)
        }
    }
}

@Composable
fun SubscriptionStatusBar(status: SubscriptionStatus, remainingQuestions: Int) {
    when (status) {
        SubscriptionStatus.ACTIVE -> Text("Subscription: Active", color = MaterialTheme.colorScheme.primary)
        SubscriptionStatus.EXPIRED -> Text("Subscription: Expired", color = MaterialTheme.colorScheme.error)
        SubscriptionStatus.NOT_SUBSCRIBED -> Text("Remaining free questions: $remainingQuestions")
    }
}

@Composable
fun QuestionInput(
    question: String,
    onQuestionChange: (String) -> Unit,
    onAskQuestion: () -> Unit,
    isLoading: Boolean,
    isEnabled: Boolean
) {
    OutlinedTextField(
        value = question,
        onValueChange = onQuestionChange,
        label = { Text("Ask a health question") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onAskQuestion,
            enabled = !isLoading && question.isNotBlank() && isEnabled
        ) {
            Text("Ask")
        }
    }
}

@Composable
fun HealthAdviceCard(advice: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Health Advice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(advice)
        }
    }
}

@Composable
fun PastQuestionsList(pastQuestions: List<Pair<String, String>>) {
    Text(
        "Past Questions",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    LazyColumn {
        items(pastQuestions) { (question, answer) ->
            PastQuestionItem(question, answer)
        }
    }
}

@Composable
fun PastQuestionItem(question: String, answer: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                answer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
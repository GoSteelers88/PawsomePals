package com.example.pawsomepals.ui.theme


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QuestionnaireScreen(
    onComplete: (Map<String, String>) -> Unit
) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    val answers = remember { mutableStateMapOf<String, String>() }

    val questions = listOf(
        "What's your dog's name?",
        "What breed is your dog?",
        "How old is your dog?",
        "What's your dog's gender?",
        "Is your dog spayed/neutered?",
        "What's your dog's size?",
        "How would you describe your dog's energy level?",
        "Is your dog friendly with other dogs?",
        "Is your dog friendly with children?",
        "Does your dog have any special needs or medical conditions?",
        "What's your dog's favorite toy?",
        "Does your dog prefer indoor or outdoor activities?",
        "How often do you take your dog for walks?",
        "What's your dog's favorite treat?",
        "Does your dog have any training certifications?"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Question ${currentQuestionIndex + 1} of ${questions.size}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            questions[currentQuestionIndex],
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        var answer by remember { mutableStateOf("") }
        TextField(
            value = answer,
            onValueChange = { answer = it },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                answers[questions[currentQuestionIndex]] = answer
                if (currentQuestionIndex < questions.size - 1) {
                    currentQuestionIndex++
                    answer = ""
                } else {
                    onComplete(answers)
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (currentQuestionIndex < questions.size - 1) "Next" else "Complete")
        }
    }
}
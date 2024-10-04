package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pawsomepals.viewmodel.QuestionnaireViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireScreen(
    viewModel: QuestionnaireViewModel,
    userId: String,
    onComplete: () -> Unit
) {
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var customAnswer by remember { mutableStateOf("") }

    val questions = listOf(
        Question("What's your dog's name?", QuestionType.TEXT),
        Question("What breed is your dog?", QuestionType.SINGLE_CHOICE, listOf(
            "Labrador Retriever", "German Shepherd", "Golden Retriever", "French Bulldog",
            "Bulldog", "Poodle", "Beagle", "Rottweiler", "Pointer", "Dachshund",
            "Yorkshire Terrier", "Boxer", "Siberian Husky", "Great Dane", "Doberman Pinscher", "Other"
        )),
        Question("How old is your dog?", QuestionType.SINGLE_CHOICE, listOf(
            "Less than 1 year", "1-3 years", "4-7 years", "8-10 years", "Over 10 years"
        )),
        Question("What's your dog's gender?", QuestionType.SINGLE_CHOICE, listOf("Male", "Female")),
        Question("Is your dog spayed/neutered?", QuestionType.SINGLE_CHOICE, listOf("Yes", "No")),
        Question("What's your dog's size?", QuestionType.SINGLE_CHOICE, listOf(
            "Extra Small (0-10 lbs)", "Small (11-25 lbs)", "Medium (26-50 lbs)",
            "Large (51-100 lbs)", "Extra Large (100+ lbs)"
        )),
        Question("How would you describe your dog's energy level?", QuestionType.SINGLE_CHOICE, listOf(
            "Very Low", "Low", "Moderate", "High", "Very High"
        )),
        Question("Is your dog friendly with other dogs?", QuestionType.SINGLE_CHOICE, listOf(
            "Very Friendly", "Friendly", "Neutral", "Somewhat Unfriendly", "Not Friendly"
        )),
        Question("Is your dog friendly with children?", QuestionType.SINGLE_CHOICE, listOf(
            "Very Friendly", "Friendly", "Neutral", "Somewhat Unfriendly", "Not Friendly", "Unknown"
        )),
        Question("Does your dog have any special needs or medical conditions?", QuestionType.MULTI_CHOICE, listOf(
            "None", "Allergies", "Arthritis", "Blindness", "Deafness", "Diabetes",
            "Heart Condition", "Hip Dysplasia", "Skin Condition", "Other"
        )),
        Question("What's your dog's favorite toy?", QuestionType.SINGLE_CHOICE, listOf(
            "Ball", "Frisbee", "Plush Toy", "Rope Toy", "Chew Toy", "Puzzle Toy", "None", "Other"
        )),
        Question("Does your dog prefer indoor or outdoor activities?", QuestionType.SINGLE_CHOICE, listOf(
            "Mostly Indoor", "Balanced Indoor/Outdoor", "Mostly Outdoor"
        )),
        Question("How often do you take your dog for walks?", QuestionType.SINGLE_CHOICE, listOf(
            "Multiple times a day", "Once a day", "A few times a week", "Once a week", "Rarely"
        )),
        Question("What's your dog's favorite treat?", QuestionType.SINGLE_CHOICE, listOf(
            "Commercial dog treats", "Cheese", "Meat", "Fruits/Vegetables", "Homemade treats", "Other"
        )),
        Question("Does your dog have any training certifications?", QuestionType.MULTI_CHOICE, listOf(
            "None", "Basic Obedience", "Advanced Obedience", "Agility", "Therapy Dog",
            "Search and Rescue", "Canine Good Citizen", "Other"
        ))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentQuestionIndex < questions.size) {
            Text(
                "Question ${currentQuestionIndex + 1} of ${questions.size}",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                questions[currentQuestionIndex].text,
                style = MaterialTheme.typography.bodyLarge
            )

            when (questions[currentQuestionIndex].type) {
            QuestionType.TEXT -> {
                TextField(
                    value = customAnswer,
                    onValueChange = {
                        customAnswer = it
                        answers[questions[currentQuestionIndex].text] = it
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            QuestionType.SINGLE_CHOICE -> {
                questions[currentQuestionIndex].options?.forEach { option ->
                    Button(
                        onClick = {
                            answers[questions[currentQuestionIndex].text] = option
                            if (option != "Other") {
                                moveToNextQuestion(currentQuestionIndex, questions.size, answers, "") { newIndex, newAnswers ->
                                    currentQuestionIndex = newIndex
                                    answers.clear()
                                    answers.putAll(newAnswers)
                                    customAnswer = ""
                                }
                            } else {
                                customAnswer = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(option)
                    }
                }
                if (questions[currentQuestionIndex].options?.contains("Other") == true) {
                    TextField(
                        value = customAnswer,
                        onValueChange = {
                            customAnswer = it
                            answers[questions[currentQuestionIndex].text] = "Other: $it"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Other (please specify)") }
                    )
                }
            }
            QuestionType.MULTI_CHOICE -> {
                val selectedOptions = remember { mutableStateListOf<String>() }
                questions[currentQuestionIndex].options?.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = selectedOptions.contains(option),
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    selectedOptions.add(option)
                                } else {
                                    selectedOptions.remove(option)
                                }
                                answers[questions[currentQuestionIndex].text] = selectedOptions.joinToString(", ")
                            }
                        )
                        Text(option)
                    }
                }
                if (questions[currentQuestionIndex].options?.contains("Other") == true) {
                    TextField(
                        value = customAnswer,
                        onValueChange = {
                            customAnswer = it
                            if (it.isNotEmpty()) {
                                selectedOptions.removeAll { it.startsWith("Other:") }
                                selectedOptions.add("Other: $it")
                            } else {
                                selectedOptions.removeAll { it.startsWith("Other:") }
                            }
                            answers[questions[currentQuestionIndex].text] = selectedOptions.joinToString(", ")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Other (please specify)") }
                    )
                }
            }
        }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentQuestionIndex > 0) {
                    Button(onClick = {
                        currentQuestionIndex--
                        customAnswer = answers[questions[currentQuestionIndex].text] ?: ""
                    }) {
                        Text("Previous")
                    }
                }
                Button(
                    onClick = {
                        val currentQuestion = questions[currentQuestionIndex]
                        when (currentQuestion.type) {
                            QuestionType.TEXT -> {
                                if (customAnswer.isNotEmpty()) {
                                    answers[currentQuestion.text] = customAnswer
                                }
                            }
                            QuestionType.SINGLE_CHOICE -> {
                                if (!answers.containsKey(currentQuestion.text) && customAnswer.isEmpty()) {
                                    // Show an error or prevent moving to the next question
                                    return@Button
                                }
                            }
                            QuestionType.MULTI_CHOICE -> {
                                if (!answers.containsKey(currentQuestion.text) && customAnswer.isEmpty()) {
                                    // Show an error or prevent moving to the next question
                                    return@Button
                                }
                            }
                        }

                        if (currentQuestionIndex < questions.size - 1) {
                            currentQuestionIndex++
                            customAnswer = ""
                        } else {
                            viewModel.saveQuestionnaireResponses(userId, answers)
                            onComplete()
                        }
                    }
                ) {
                    Text(if (currentQuestionIndex < questions.size - 1) "Next" else "Complete")
                }
            }

            LinearProgressIndicator(
                progress = (currentQuestionIndex + 1).toFloat() / questions.size,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text("Questionnaire completed!", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onComplete) {
                Text("Finish")
            }
        }
    }
}

enum class QuestionType {
    TEXT, SINGLE_CHOICE, MULTI_CHOICE
}

data class Question(
    val text: String,
    val type: QuestionType,
    val options: List<String>? = null
)

private fun moveToNextQuestion(
    currentIndex: Int,
    totalQuestions: Int,
    answers: MutableMap<String, String>,
    customAnswer: String,
    updateUI: (Int, Map<String, String>) -> Unit
) {
    val newAnswers = answers.toMutableMap()
    if (customAnswer.isNotEmpty() && answers.isNotEmpty()) {
        newAnswers[answers.keys.last()] = customAnswer
    }
    val newIndex = if (currentIndex < totalQuestions - 1) currentIndex + 1 else totalQuestions
    updateUI(newIndex, newAnswers)
}
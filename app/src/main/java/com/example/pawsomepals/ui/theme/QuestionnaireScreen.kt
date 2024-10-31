package com.example.pawsomepals.ui.theme


import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pawsomepals.ui.components.Question
import com.example.pawsomepals.ui.components.QuestionProgress
import com.example.pawsomepals.ui.components.QuestionType
import com.example.pawsomepals.ui.components.QuestionnaireData
import com.example.pawsomepals.viewmodel.QuestionnaireViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireScreen(
    viewModel: QuestionnaireViewModel,
    userId: String,
    dogId: String?,
    onComplete: (Map<String, String>) -> Unit,  // Changed type to Map<String, String>
    onExit: () -> Unit
) {
    // State Management
    val scope = rememberCoroutineScope()
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf(emptyMap<String, String>()) }
    var showCelebration by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasCalledOnComplete by remember { mutableStateOf(false) }


    // Collect ViewModel States
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val questionnaireResponses by viewModel.questionnaireResponses.collectAsState()
    val completionStatus by viewModel.completionStatus.collectAsState()

    // Load Questions
    val questionnaireCategories = remember { QuestionnaireData.getQuestionnaireCategories() }
    val allQuestions = remember { questionnaireCategories.flatMap { it.questions } }
    val currentQuestion = remember(currentQuestionIndex) { allQuestions[currentQuestionIndex] }

    // Progress Calculation
    val currentProgress = remember(currentQuestionIndex) {
        val categoryIndex = questionnaireCategories.indexOfFirst { category ->
            category.questions.contains(currentQuestion)
        }
        QuestionProgress(
            categoryIndex = categoryIndex,
            questionIndex = currentQuestionIndex,
            totalQuestions = allQuestions.size,
            categoryName = questionnaireCategories[categoryIndex].name
        )
    }

    val progress by animateFloatAsState(
        targetValue = (currentQuestionIndex + 1).toFloat() / allQuestions.size,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
    )

    // Effects
    LaunchedEffect(dogId) {
        if (dogId != null) {
            try {
                // Load existing dog profile data
                viewModel.loadExistingDogProfile(dogId)

                // Wait for responses to be loaded
                viewModel.questionnaireResponses.collect { responses ->
                    if (responses.isNotEmpty()) {
                        answers = responses
                        // Find the first question that matches each loaded answer
                        allQuestions.forEachIndexed { index, question ->
                            if (responses.containsKey(question.id)) {
                                currentQuestionIndex = index
                                return@collect
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("QuestionnaireScreen", "Error loading dog profile", e)
                viewModel.setErrorMessage("Failed to load dog profile: ${e.message}")
            }
        }
    }



    LaunchedEffect(error) {
        if (error != null) {
            errorMessage = error ?: "An unknown error occurred"
            showErrorDialog = true
            viewModel.clearError()
        }
    }

    LaunchedEffect(completionStatus) {
        if (completionStatus && !hasCalledOnComplete) {
            showCelebration = true
            delay(3000) // Wait for celebration animation
            hasCalledOnComplete = true
            onComplete(answers)
        }
    }


    // Back Handler
    BackHandler {
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--
        } else {
            showExitConfirmDialog = true
        }
    }

    // Helper Functions
    fun validateAnswer(answer: String?): Boolean = QuestionnaireData.validateAnswer(currentQuestion, answer)

    fun moveToNextQuestion() {
        val currentAnswer = answers[currentQuestion.id]
        Log.d("QuestionnaireScreen", "Moving to next question. Current: $currentQuestionIndex, Total: ${allQuestions.size}")

        if (validateAnswer(currentAnswer)) {
            if (currentQuestionIndex < allQuestions.size - 1) {
                currentQuestionIndex++
                Log.d("QuestionnaireScreen", "Advanced to question: $currentQuestionIndex")
            } else {
                Log.d("QuestionnaireScreen", "Final question reached. Saving responses...")
                scope.launch {
                    try {
                        viewModel.saveQuestionnaireResponses(userId, dogId, answers)
                        Log.d("QuestionnaireScreen", "Responses saved successfully")
                    } catch (e: Exception) {
                        Log.e("QuestionnaireScreen", "Error in completion flow", e)
                        viewModel.setErrorMessage("Failed to complete: ${e.message}")
                    }
                }
            }
        } else {
            viewModel.setErrorMessage("Please provide a valid answer before continuing")
        }
    }

    // Back handler
    BackHandler {
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--
        } else {
            showExitConfirmDialog = true
        }
    }

    // UI Structure
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pet Profile") },
                navigationIcon = {
                    IconButton(onClick = { showExitConfirmDialog = true }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Exit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            QuestionnaireContent(
                currentProgress = QuestionProgress(
                    categoryIndex = questionnaireCategories.indexOfFirst { it.questions.contains(currentQuestion) },
                    questionIndex = currentQuestionIndex,
                    totalQuestions = allQuestions.size,
                    categoryName = questionnaireCategories[questionnaireCategories.indexOfFirst {
                        it.questions.contains(currentQuestion)
                    }].name
                ),
                progress = (currentQuestionIndex + 1).toFloat() / allQuestions.size,
                currentQuestion = currentQuestion,
                answers = answers,
                onAnswerSelected = { answer ->
                    answers = answers.toMutableMap().apply {
                        put(currentQuestion.id, answer)
                    }
                    if (currentQuestion.type == QuestionType.SINGLE_CHOICE) {
                        moveToNextQuestion()
                    }
                },
                currentQuestionIndex = currentQuestionIndex,
                canGoBack = currentQuestionIndex > 0,
                canGoForward = when (currentQuestion.type) {
                    QuestionType.TEXT -> answers[currentQuestion.id]?.isNotEmpty() == true
                    QuestionType.SINGLE_CHOICE -> answers.containsKey(currentQuestion.id)
                    QuestionType.MULTI_CHOICE -> answers[currentQuestion.id]?.isNotEmpty() == true
                },
                onPrevious = { currentQuestionIndex-- },
                onNext = { moveToNextQuestion() }
            )
        }
    }

    // Overlays and Dialogs
    if (showCelebration) {
        CelebrationOverlay {
            showCelebration = false
        }
    }

    if (showErrorDialog) {
        QuestionnaireErrorDialog(
            message = errorMessage,
            onDismiss = { showErrorDialog = false }
        )
    }

    if (showExitConfirmDialog) {
        ExitConfirmationDialog(
            onConfirm = {
                scope.launch {
                    viewModel.saveQuestionnaireResponses(userId, dogId, answers)
                    showExitConfirmDialog = false
                    onExit()
                }
            },
            onDismiss = { showExitConfirmDialog = false }
        )
    }
}

@Composable
fun CelebrationOverlay(onDismiss: () -> Unit) {
    var celebrationStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(1000)
        celebrationStep = 1
        delay(1000)
        celebrationStep = 2
        delay(1000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (celebrationStep) {
                    0 -> "Woohoo!"
                    1 -> "You did it!"
                    else -> "Welcome to the pack!"
                },
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Icon(
                imageVector = when (celebrationStep) {
                    0 -> Icons.Filled.Celebration
                    1 -> Icons.Filled.Pets
                    else -> Icons.Filled.Favorite
                },
                contentDescription = "Celebration icon",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

@Composable
private fun QuestionnaireContent(
    currentProgress: QuestionProgress,
    progress: Float,
    currentQuestion: Question,
    answers: Map<String, String>,
    onAnswerSelected: (String) -> Unit,
    currentQuestionIndex: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .heightIn(min = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QuestionnaireHeader(currentProgress)
        Spacer(modifier = Modifier.height(16.dp))
        PetProgressIndicator(progress)
        Spacer(modifier = Modifier.height(24.dp))

        @OptIn(ExperimentalAnimationApi::class)
        AnimatedContent(
            targetState = currentQuestionIndex,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = slideInHorizontally { width -> width } + fadeIn(),
                    initialContentExit = slideOutHorizontally { width -> -width } + fadeOut()
                )
            },
            label = "Question Animation"
        ) { targetIndex ->
            QuestionCard(
                question = currentQuestion,
                answer = answers[currentQuestion.id] ?: "",
                onAnswerSelected = onAnswerSelected
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        NavigationButtons(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onPrevious = onPrevious,
            onNext = onNext
        )
    }
}

@Composable
private fun QuestionCard(
    question: Question,
    answer: String,
    onAnswerSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = question.text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (question.type) {
                QuestionType.TEXT -> TextAnswerField(answer, onAnswerSelected)
                QuestionType.SINGLE_CHOICE -> SingleChoiceAnswers(question, answer, onAnswerSelected)
                QuestionType.MULTI_CHOICE -> MultiChoiceAnswers(
                    question = question,
                    selectedAnswers = if (answer.isBlank()) emptyList() else answer.split(","),
                    onAnswerSelected = onAnswerSelected
                )
            }
        }
    }
}

@Composable
private fun NavigationButtons(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AnimatedVisibility(
            visible = canGoBack,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Button(
                onClick = onPrevious,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Previous")
                Spacer(Modifier.width(8.dp))
                Text("Previous")
            }
        }

        Button(
            onClick = onNext,
            enabled = canGoForward,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text("Next")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = "Next")
        }
    }
}

@Composable
private fun TextAnswerField(answer: String, onAnswerSelected: (String) -> Unit) {
    OutlinedTextField(
        value = answer,
        onValueChange = onAnswerSelected,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Your answer") }
    )
}

@Composable
private fun SingleChoiceAnswers(
    question: Question,
    selectedAnswer: String,
    onAnswerSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
    ) {
        question.options?.forEach { option ->
            ChoiceButton(
                text = option,
                selected = option == selectedAnswer,
                onClick = { onAnswerSelected(option) }
            )
        }
    }
}

@Composable
private fun MultiChoiceAnswers(
    question: Question,
    selectedAnswers: List<String>,
    onAnswerSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
    ) {
        question.options?.forEach { option ->
            ChoiceButton(
                text = option,
                selected = selectedAnswers.contains(option),
                onClick = {
                    val newAnswers = when {
                        option == "None" -> listOf("None")
                        selectedAnswers.contains("None") -> listOf(option)
                        selectedAnswers.contains(option) -> selectedAnswers.filter { it != option }
                        else -> selectedAnswers + listOf(option)
                    }
                    onAnswerSelected(newAnswers.joinToString(","))
                }
            )
        }
    }
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text)
    }
}

@Composable
private fun QuestionnaireHeader(progress: QuestionProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = progress.categoryName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            progress = progress.questionIndex.toFloat() / progress.totalQuestions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun PetProgressIndicator(progress: Float) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .border(8.dp, MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            color = MaterialTheme.colorScheme.secondary
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Complete",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun QuestionnaireErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun ExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exit Questionnaire?") },
        text = { Text("Your progress will be saved. Are you sure you want to exit?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}
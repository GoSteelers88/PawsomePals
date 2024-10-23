package com.example.pawsomepals

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pawsomepals.ui.theme.*
import com.example.pawsomepals.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.time.Duration.Companion.milliseconds


sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object TermsOfService : Screen("terms_of_service")
    object Questionnaire : Screen("questionnaire/{userId}/{dogId}") {
        fun createRoute(userId: String, dogId: String? = null): String {
            return "questionnaire/$userId/${dogId ?: "none"}"
        }
    }

    object MainScreen : Screen("main_screen")
    object Profile : Screen("profile/{userId}") {
        fun createRoute(userId: String) = "profile/${URLEncoder.encode(userId, "UTF-8")}"
    }
    object DogProfile : Screen("dog_profile/{dogId}") {
        fun createRoute(dogId: String) = "dog_profile/${URLEncoder.encode(dogId, "UTF-8")}"
    }
    object AddEditDogProfile : Screen("add_edit_dog_profile/{dogId}") {
        fun createRoute(dogId: String = "new") = "add_edit_dog_profile/$dogId"
    }
    object AddDogQuestionnaire : Screen("add_dog_questionnaire")  // Add this new route

    object Playdate : Screen("playdate/{playdateId}") {
        fun createRoute(playdateId: String) = "playdate/${URLEncoder.encode(playdateId, "UTF-8")}"
    }
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/${URLEncoder.encode(chatId, "UTF-8")}"
    }
    object HealthAdvisor : Screen("health_advisor")
    object Settings : Screen("settings")
    object PhotoManagement : Screen("photo_management")
    object Rating : Screen("rating/{ratingId}") {
        fun createRoute(ratingId: String) = "rating/${URLEncoder.encode(ratingId, "UTF-8")}"
    }
    object Notifications : Screen("notifications")
    object Swiping : Screen("swiping")
    object TrainerTips : Screen("trainer_tips")
}
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    dogProfileViewModel: DogProfileViewModel,
    playdateViewModel: PlaydateViewModel,
    chatViewModel: ChatViewModel,
    healthAdvisorViewModel: HealthAdvisorViewModel,
    settingsViewModel: SettingsViewModel,
    photoManagementViewModel: PhotoManagementViewModel,
    questionnaireViewModel: QuestionnaireViewModel,
    ratingViewModel: RatingViewModel,
    notificationViewModel: NotificationViewModel,
    swipingViewModel: SwipingViewModel,
    trainerTipsViewModel: TrainerTipsViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val isUserFullyLoaded by authViewModel.isUserFullyLoaded.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    LaunchedEffect(authState, isUserFullyLoaded) {
        when {
            !isUserFullyLoaded -> {
                Log.d("Navigation", "Waiting for user data to load")
                return@LaunchedEffect
            }
            authState == AuthViewModel.AuthState.Authenticated -> {
                val userId = currentUser?.id ?: return@LaunchedEffect
                Log.d("Navigation", "User authenticated: $userId")

                when {
                    currentUser?.hasAcceptedTerms == false -> {
                        navController.navigate(Screen.TermsOfService.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    currentUser?.hasCompletedQuestionnaire == false -> {
                        navController.navigate(Screen.Questionnaire.createRoute(userId)) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    else -> {
                        navController.navigate(Screen.MainScreen.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            authState == AuthViewModel.AuthState.Unauthenticated -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(
                authViewModel = authViewModel,
                navController = navController
            )
        }
        // In AddEditDogProfile navigation
        composable(
            route = Screen.AddEditDogProfile.route,
            arguments = listOf(navArgument("dogId") { type = NavType.StringType })
        ) { backStackEntry ->
            val dogId = backStackEntry.arguments?.getString("dogId") ?: "new"
            val currentUserId = authViewModel.currentUser.collectAsState().value?.id

            AddEditDogProfileScreen(
                viewModel = profileViewModel,
                dogId = dogId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToQuestionnaire = { existingDogId ->
                    if (currentUserId != null) {
                        // Update this navigation call to match the new route pattern
                        navController.navigate(Screen.Questionnaire.createRoute(
                            userId = currentUserId,
                            dogId = existingDogId
                        ))
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                navController = navController
            )
        }

        composable(Screen.AddDogQuestionnaire.route) {
            val currentUserId = authViewModel.currentUser.collectAsState().value?.id ?: ""
            QuestionnaireScreen(
                viewModel = questionnaireViewModel,
                userId = currentUserId,
                dogId = null,  // Will be generated when saving
                onComplete = {
                    // After questionnaire completion, navigate back to profile
                    navController.popBackStack()
                },
                onExit = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterClick = { username, email, password, confirmPassword, petName ->
                    authViewModel.registerUser(username, email, password, petName)
                },
                onLoginClick = { navController.popBackStack() },
                isLoading = authViewModel.isLoading.collectAsState().value,
                errorMessage = authViewModel.errorMessage.collectAsState().value
            )
        }

        composable(Screen.TermsOfService.route) {
            TermsOfServiceScreen(
                onAccept = {  // Remove the @Composable here
                    authViewModel.acceptTerms()
                    val currentUserId = authViewModel.currentUser.value?.id ?: ""
                    navController.navigate(Screen.Questionnaire.createRoute(currentUserId)) {
                        popUpTo(Screen.TermsOfService.route) { inclusive = true }
                    }
                },
                onDecline = {
                    authViewModel.logOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // In your Navigation.kt, update the Questionnaire composable:

        composable(
            route = "questionnaire/{userId}/{dogId}",
            arguments = listOf(
                navArgument("userId") {
                    type = NavType.StringType
                },
                navArgument("dogId") {
                    type = NavType.StringType
                    defaultValue = "none"
                }
            )
        ) { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("userId") ?: ""
            val dogId = backStackEntry.arguments?.getString("dogId")?.takeIf { it != "none" }
            val scope = rememberCoroutineScope()
            var showingCelebration by remember { mutableStateOf(false) }
            val completionStatus by questionnaireViewModel.completionStatus.collectAsState()

            // Single LaunchedEffect for completion handling
            LaunchedEffect(completionStatus) {
                if (completionStatus) {
                    Log.d("Navigation", "Questionnaire completed, preparing navigation")
                    showingCelebration = true
                    delay(3000) // Celebration animation duration

                    if (dogId == null) {
                        navController.navigate(Screen.MainScreen.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            }

            QuestionnaireScreen(
                viewModel = questionnaireViewModel,
                userId = currentUserId,
                dogId = dogId,
                onComplete = { answers ->
                    scope.launch {
                        try {
                            Log.d("Navigation", "Saving questionnaire responses")
                            questionnaireViewModel.saveQuestionnaireResponses(currentUserId, dogId, answers as Map<String, String>)
                            if (dogId == null) {
                                authViewModel.setQuestionnaireCompleted(true)
                            }
                        } catch (e: Exception) {
                            Log.e("Navigation", "Error during questionnaire completion", e)
                        }
                    }
                },
                onExit = {
                    navController.popBackStack()
                }
            )

            if (showingCelebration) {
                CelebrationOverlay {
                    showingCelebration = false
                }
            }
        }

        composable(Screen.MainScreen.route) {
            val currentUser by authViewModel.currentUser.collectAsState()
            val isUserSetupComplete by remember {
                derivedStateOf {
                    currentUser?.hasAcceptedTerms == true && currentUser?.hasCompletedQuestionnaire == true
                }
            }

            LaunchedEffect(currentUser, isUserSetupComplete) {
                if (!isUserSetupComplete) {
                    when {
                        currentUser == null -> {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.MainScreen.route) { inclusive = true }
                            }
                        }
                        !currentUser!!.hasAcceptedTerms -> {
                            navController.navigate(Screen.TermsOfService.route) {
                                popUpTo(Screen.MainScreen.route) { inclusive = true }
                            }
                        }
                        !currentUser!!.hasCompletedQuestionnaire -> {
                            val userId = currentUser!!.id
                            navController.navigate(Screen.Questionnaire.createRoute(userId)) {
                                popUpTo(Screen.MainScreen.route) { inclusive = true }
                            }
                        }
                    }
                }
            }

            if (isUserSetupComplete) {
                MainScreen(
                    navController = navController,
                    username = currentUser?.username ?: "",
                    profileViewModel = profileViewModel,
                    dogProfileViewModel = dogProfileViewModel,
                    playdateViewModel = playdateViewModel,
                    chatViewModel = chatViewModel,
                    healthAdvisorViewModel = healthAdvisorViewModel,
                    settingsViewModel = settingsViewModel,
                    photoManagementViewModel = photoManagementViewModel,
                    ratingViewModel = ratingViewModel,
                    notificationViewModel = notificationViewModel,
                    swipingViewModel = swipingViewModel,
                    onLogout = {
                        authViewModel.logOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.MainScreen.route) { inclusive = true }
                        }
                    },
                    onProfileClick = { userId -> navController.navigate(Screen.Profile.createRoute(userId)) },
                    onDogProfileClick = { navController.navigate(Screen.DogProfile.route) },
                    onPlaydateClick = { playdateId -> navController.navigate(Screen.Playdate.createRoute(playdateId)) },
                    onChatClick = { chatId -> navController.navigate(Screen.Chat.createRoute(chatId)) },
                    onHealthAdvisorClick = { navController.navigate(Screen.HealthAdvisor.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onPhotoManagementClick = { navController.navigate(Screen.PhotoManagement.route) },
                    onRatingClick = { navController.navigate(Screen.Rating.route) },
                    onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                    onSwipeClick = { navController.navigate(Screen.Swiping.route) },
                    onSwipeScreenClick = { navController.navigate(Screen.Swiping.route) },
                    onChatListClick = { navController.navigate(Screen.Chat.createRoute("")) },
                    onSchedulePlaydateClick = { navController.navigate(Screen.Playdate.createRoute("")) },
                    onPlaydateRequestsClick = { /* Add navigation or action for playdate requests */ },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        composable(
            route = Screen.Profile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val providedUserId = backStackEntry.arguments?.getString("userId") ?: ""
            val currentUserId = authViewModel.getCurrentUserId() ?: ""
            val effectiveUserId = providedUserId.ifBlank { currentUserId }

            if (effectiveUserId.isNotBlank()) {
                ProfileScreen(
                    viewModel = profileViewModel,
                    userId = effectiveUserId,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToAddDog = { navController.navigate(Screen.AddEditDogProfile.createRoute()) }
                )
            } else {
                ErrorScreen(
                    errorMessage = "Unable to load profile. Please try again.",
                    onRetry = {
                        navController.navigate(navController.currentBackStackEntry?.destination?.route ?: Screen.MainScreen.route)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }





        composable(Screen.Playdate.route) { backStackEntry ->
            val playdateId = backStackEntry.arguments?.getString("playdateId") ?: ""
            PlaydateScreen(
                viewModel = playdateViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSchedulePlaydate = {
                    navController.navigate(Screen.Playdate.createRoute("new"))
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = URLDecoder.decode(backStackEntry.arguments?.getString("chatId"), "UTF-8")
            ChatScreen(
                chatId = chatId,
                viewModel = chatViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.HealthAdvisor.route) {
            HealthAdvisorScreen(
                viewModel = healthAdvisorViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                viewModel = settingsViewModel
            )
        }

        composable(Screen.PhotoManagement.route) {
            PhotoManagementScreen(
                viewModel = photoManagementViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Rating.route) {
            RatingScreen(
                ratingId = "",
                viewModel = ratingViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                viewModel = notificationViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Swiping.route) {
            SwipingScreen(
                viewModel = swipingViewModel,
                onSchedulePlaydate = { playdateId ->
                    navController.navigate(Screen.Playdate.createRoute(playdateId))
                }
            )
        }

        composable(Screen.TrainerTips.route) {
            TrainerTipsScreen(
                viewModel = trainerTipsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
package com.example.pawsomepals

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pawsomepals.ui.theme.*
import com.example.pawsomepals.viewmodel.*

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
    ratingViewModel: RatingViewModel,
    notificationViewModel: NotificationViewModel,
    swipingViewModel: SwipeViewModel,
    trainerTipsViewModel: TrainerTipsViewModel,
    questionnaireViewModel: QuestionnaireViewModel
) {
    val navController = rememberNavController()
    val currentUser by authViewModel.currentUser.collectAsState()
    val hasAcceptedTerms by authViewModel.hasAcceptedTerms.collectAsState(initial = false)
    val hasCompletedQuestionnaire by authViewModel.hasCompletedQuestionnaire.collectAsState(initial = false)

    LaunchedEffect(currentUser, hasAcceptedTerms, hasCompletedQuestionnaire) {
        currentUser?.let {
            when {
                !hasAcceptedTerms -> navController.navigate("terms_of_service")
                !hasCompletedQuestionnaire -> navController.navigate("questionnaire")
                else -> navController.navigate("main_screen") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            val isLoading by authViewModel.isLoading.collectAsState()
            val errorMessage by authViewModel.errorMessage.collectAsState()

            LoginScreen(
                onLoginClick = { email, password ->
                    authViewModel.loginUser(
                        email,
                        password,
                        onSuccess = {
                            navController.navigate("main_screen") {
                                popUpTo("login") { inclusive = true }
                            }
                        },
                        onError = { /* Error handling is done in ViewModel */ }
                    )
                },
                onRegisterClick = { navController.navigate("register") },
                onGoogleSignInClick = {
                    // Implement Google Sign-In logic
                },
                onFacebookSignInClick = {
                    // Implement Facebook Sign-In logic
                },
                isLoading = isLoading,
                errorMessage = errorMessage
            )
        }

        composable("register") {
            val isLoading by authViewModel.isLoading.collectAsState()
            val errorMessage by authViewModel.errorMessage.collectAsState()
            RegisterScreen(
                onRegisterClick = { username, email, password, confirmPassword, petName ->
                    authViewModel.registerUser(username, email, password, petName,
                        onSuccess = {
                            authViewModel.clearErrorMessage()
                            navController.navigate("terms_of_service") {
                                popUpTo("register") { inclusive = true }
                            }
                        },
                        onError = { error ->
                            authViewModel.updateErrorMessage(error)
                        }
                    )
                },
                onLoginClick = { navController.popBackStack() },
                isLoading = isLoading,
                errorMessage = errorMessage
            )
        }

        composable("terms_of_service") {
            TermsOfServiceScreen(
                onAccept = {
                    authViewModel.acceptTerms()
                    navController.navigate("questionnaire")
                },
                onDecline = {
                    authViewModel.logOut()
                    navController.navigate("login") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("questionnaire") {
            val userId = authViewModel.currentUser.value?.id ?: return@composable
            QuestionnaireScreen(
                viewModel = questionnaireViewModel,
                userId = userId,
                onComplete = {
                    authViewModel.setQuestionnaireCompleted(true)
                    navController.navigate("main_screen") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("main_screen") {
            MainScreen(
                navController = navController,
                onProfileClick = { navController.navigate("user_profile") },
                onDogProfileClick = { navController.navigate("dog_profile") },
                onSwipeScreenClick = { navController.navigate("swiping_screen") },
                onChatListClick = { navController.navigate("chat_list") },
                onSchedulePlaydateClick = { navController.navigate("playdate_calendar") },
                onPlaydateRequestsClick = { navController.navigate("playdate_requests") },
                onTrainerClick = { navController.navigate("trainer_tips") },
                onHealthAdvisorClick = { navController.navigate("health_advisor") },
                onPhotoManagementClick = { navController.navigate("photo_management") },
                onRatingClick = { navController.navigate("rating") },
                onNotificationsClick = { navController.navigate("notifications") },
                onSettingsClick = { navController.navigate("settings") },
                username = authViewModel.currentUser.value?.username ?: "User"
            )
        }

        composable("user_profile") {
            UserProfileScreen(
                viewModel = profileViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("dog_profile") {
            DogProfileScreen(
                viewModel = dogProfileViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("swiping_screen") {
            SwipingScreen(
                viewModel = swipingViewModel,
                onSchedulePlaydate = { dogId -> navController.navigate("playdate_scheduling/$dogId") }
            )
        }

        composable("chat_list") {
            ChatListScreen(
                viewModel = chatViewModel,
                navigateToChat = { chatId -> navController.navigate("chat/$chatId") },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("chat/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            ChatScreen(
                chatId = chatId,
                viewModel = chatViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("playdate_calendar") {
            PlaydateCalendarScreen(
                viewModel = playdateViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("playdate_requests") {
            PlaydateRequestsScreen(
                viewModel = playdateViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("playdate_scheduling/{dogId}") { backStackEntry ->
            val dogId = backStackEntry.arguments?.getString("dogId") ?: return@composable
            PlaydateSchedulingScreen(
                viewModel = playdateViewModel,
                profileId = dogId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("trainer_tips") {
            TrainerTipsScreen(
                viewModel = trainerTipsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("health_advisor") {
            HealthAdvisorScreen(
                viewModel = healthAdvisorViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(navController = navController)
        }

        composable("questionnaire") {
            val userId = authViewModel.currentUser.value?.id ?: return@composable
            QuestionnaireScreen(
                viewModel = questionnaireViewModel,
                userId = userId,
                onComplete = {
                    authViewModel.setQuestionnaireCompleted(true)
                    navController.navigate("main_screen") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("notification_preferences") {
            NotificationPreferencesScreen(
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
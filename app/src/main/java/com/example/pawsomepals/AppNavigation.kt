package com.example.pawsomepals

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pawsomepals.ui.theme.*
import com.example.pawsomepals.viewmodel.*
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.pawsomepals.ui.theme.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object TermsOfService : Screen("terms_of_service")
    object Questionnaire : Screen("questionnaire")
    object MainScreen : Screen("main_screen")
    object Profile : Screen("profile/{userId}") {
        fun createRoute(userId: String) = "profile/${URLEncoder.encode(userId, "UTF-8")}"
    }
    object DogProfile : Screen("dog_profile/{dogId}") {
        fun createRoute(dogId: String) = "dog_profile/${URLEncoder.encode(dogId, "UTF-8")}"
    }
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

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(
                authViewModel = authViewModel,
                navController = navController
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                navController = navController
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
                onAccept = {
                    authViewModel.acceptTerms()
                    navController.navigate(Screen.Questionnaire.route)
                },
                onDecline = {
                    authViewModel.logOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Questionnaire.route) {
            val currentUserId = authViewModel.currentUser.collectAsState().value?.id ?: ""
            QuestionnaireScreen(
                viewModel = questionnaireViewModel,
                userId = currentUserId,
                onComplete = {
                    authViewModel.setQuestionnaireCompleted(true)
                    navController.navigate(Screen.MainScreen.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.MainScreen.route) {
            MainScreen(
                navController = navController,
                username = authViewModel.currentUser.collectAsState().value?.username ?: "",
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
                trainerTipsViewModel = trainerTipsViewModel,
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
                onTrainerTipsClick = { navController.navigate(Screen.TrainerTips.route) },
                onSwipeScreenClick = { navController.navigate(Screen.Swiping.route) },
                onChatListClick = { navController.navigate(Screen.Chat.createRoute("")) },
                onSchedulePlaydateClick = { navController.navigate(Screen.Playdate.createRoute("")) },
                onPlaydateRequestsClick = { /* Add navigation or action for playdate requests */ },
                onTrainerClick = { navController.navigate(Screen.TrainerTips.route) }
            )
        }

        composable(
            route = Screen.Profile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ProfileScreen(
                viewModel = profileViewModel,
                userId = userId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.DogProfile.route) { backStackEntry ->
            val dogId = backStackEntry.arguments?.getString("dogId") ?: ""
            DogProfileScreen(
                viewModel = dogProfileViewModel,
                onBackClick = { navController.popBackStack() }
            )
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
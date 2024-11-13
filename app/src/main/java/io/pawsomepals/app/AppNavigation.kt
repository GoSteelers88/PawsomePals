package io.pawsomepals.app


import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.pawsomepals.app.ui.screens.MatchesScreen
import io.pawsomepals.app.ui.screens.SwipingScreen
import io.pawsomepals.app.ui.screens.chat.ChatListScreen
import io.pawsomepals.app.ui.screens.chat.ChatScreen
import io.pawsomepals.app.ui.screens.playdate.components.LocationSearchScreen
import io.pawsomepals.app.ui.screens.playdate.components.UpcomingTab
import io.pawsomepals.app.ui.theme.AddEditDogProfileScreen
import io.pawsomepals.app.ui.theme.ErrorScreen
import io.pawsomepals.app.ui.theme.HealthAdvisorScreen
import io.pawsomepals.app.ui.theme.LoginScreen
import io.pawsomepals.app.ui.theme.MainScreen
import io.pawsomepals.app.ui.theme.NotificationsScreen
import io.pawsomepals.app.ui.theme.PhotoManagementScreen
import io.pawsomepals.app.ui.theme.PlayfulDogProfileScreen
import io.pawsomepals.app.ui.theme.ProfileScreen
import io.pawsomepals.app.ui.theme.QuestionnaireScreen
import io.pawsomepals.app.ui.theme.RatingScreen
import io.pawsomepals.app.ui.theme.RegisterScreen
import io.pawsomepals.app.ui.theme.SettingsScreen
import io.pawsomepals.app.ui.theme.SplashScreen
import io.pawsomepals.app.ui.theme.TermsOfServiceScreen
import io.pawsomepals.app.ui.theme.TrainerTipsScreen
import io.pawsomepals.app.utils.CameraPermissionManager
import io.pawsomepals.app.viewmodel.AuthViewModel
import io.pawsomepals.app.viewmodel.ChatViewModel
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.HealthAdvisorViewModel
import io.pawsomepals.app.viewmodel.LocationPermissionViewModel
import io.pawsomepals.app.viewmodel.MatchesViewModel
import io.pawsomepals.app.viewmodel.NotificationViewModel
import io.pawsomepals.app.viewmodel.PhotoManagementViewModel
import io.pawsomepals.app.viewmodel.PlaydateViewModel
import io.pawsomepals.app.viewmodel.ProfileViewModel
import io.pawsomepals.app.viewmodel.QuestionnaireViewModel
import io.pawsomepals.app.viewmodel.RatingViewModel
import io.pawsomepals.app.viewmodel.SettingsViewModel
import io.pawsomepals.app.viewmodel.SwipingViewModel
import io.pawsomepals.app.viewmodel.TrainerTipsViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder


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

    object Matches : Screen("matches") {
        fun createRoute() = route
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

    object PlayfulDogProfile : Screen("playful_dog_profile") {
        fun createRoute() = route  // Simplified since we don't need dogId parameter
    }


    object Playdate : Screen("playdate/{matchId}") {
        fun createRoute(matchId: String) = "playdate/${URLEncoder.encode(matchId, "UTF-8")}"
    }
    object ChatList : Screen("chat_list")
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

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
    trainerTipsViewModel: TrainerTipsViewModel,
    locationPermissionViewModel: LocationPermissionViewModel,
    matchesViewModel: MatchesViewModel,
    cameraPermissionManager: CameraPermissionManager  // Add this


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
                        navController.navigate(
                            Screen.Questionnaire.createRoute(
                                userId = currentUserId,
                                dogId = existingDogId
                            )
                        )
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

        composable(Screen.Matches.route) {
            MatchesScreen(
                viewModel = matchesViewModel,
                onNavigateBack = { navController.popBackStack() },
                onChatClick = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId))
                },
                onSchedulePlaydate = { dogId ->
                    navController.navigate(Screen.Playdate.createRoute(dogId))
                }
            )
        }
        composable(Screen.ChatList.route) {
            ChatListScreen(
                viewModel = chatViewModel,
                navigateToChat = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId))
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(route = Screen.PlayfulDogProfile.route) {
            PlayfulDogProfileScreen(
                viewModel = profileViewModel,
                dogProfileViewModel = dogProfileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddDog = {
                    navController.navigate(Screen.AddEditDogProfile.createRoute())
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
                onBackClick = { navController.popBackStack() },
                onSchedulePlaydate = {
                    // Navigate to PlaydateScreen
                    navController.navigate(Screen.Playdate.route)
                }
            )
        }
        // Update the composable to not pass a request parameter
        composable(
            route = Screen.Playdate.route,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType })
        ) { backStackEntry ->
            val matchId = URLDecoder.decode(backStackEntry.arguments?.getString("matchId"), "UTF-8")
            val currentMatch = playdateViewModel.getMatchDetails(matchId).collectAsState().value
            val scope = rememberCoroutineScope()

            Box(modifier = Modifier.fillMaxSize()) {
                if (currentMatch == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    UpcomingTab(
                        currentMatch = currentMatch,
                        onDismiss = { navController.popBackStack() },
                        onSchedule = { request ->
                            scope.launch {
                                playdateViewModel.createPlaydateRequest()
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
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
                dogProfileViewModel = dogProfileViewModel,
                locationPermissionViewModel = locationPermissionViewModel,
                onSchedulePlaydate = { dogId: String ->
                    navController.navigate(Screen.Playdate.createRoute(dogId))
                }
            )
        }

        composable(Screen.TrainerTips.route) {
            TrainerTipsScreen(
                viewModel = trainerTipsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("locationSearch") {
            LocationSearchScreen(
                onLocationSelected = { location ->
                    // Handle location selection
                    navController.navigate("locationDetails/${location.placeId}")
                }
            )
        }


        composable(
            route = "questionnaire/{userId}/{dogId}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("dogId") {
                    type = NavType.StringType
                    defaultValue = "none"
                }
            )
        ) { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("userId") ?: ""
            val dogId = backStackEntry.arguments?.getString("dogId")?.takeIf { it != "none" }
            QuestionnaireScreen(
                viewModel = questionnaireViewModel,
                userId = currentUserId,
                dogId = dogId,
                onComplete = {
                    navController.navigate(Screen.MainScreen.route)
                },
                onExit = {
                    navController.popBackStack()
                }
            )
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
                    dogProfileViewModel = dogProfileViewModel,
                    cameraPermissionManager = cameraPermissionManager, // Add this
                    userId = effectiveUserId,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToAddDog = { navController.navigate(Screen.AddEditDogProfile.createRoute()) }
                )
            } else {
                ErrorScreen(
                    errorMessage = "Unable to load profile. Please try again.",
                    onRetry = {
                        navController.navigate(
                            navController.currentBackStackEntry?.destination?.route
                                ?: Screen.MainScreen.route
                        )
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
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
                    onProfileClick = { userId ->
                        navController.navigate(
                            Screen.Profile.createRoute(
                                userId
                            )
                        )
                    },
                    onDogProfileClick = {
                        navController.navigate(Screen.PlayfulDogProfile.createRoute())
                    },
                    onPlaydateClick = { playdateId ->
                        navController.navigate(
                            Screen.Playdate.createRoute(
                                playdateId
                            )
                        )
                    },

                    onHealthAdvisorClick = { navController.navigate(Screen.HealthAdvisor.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onPhotoManagementClick = { navController.navigate(Screen.PhotoManagement.route) },
                    onRatingClick = {
                        navController.navigate(Screen.Rating.createRoute("new")) {
                            launchSingleTop = true
                        }
                    },
                    onNotificationsClick = {
                        navController.navigate(Screen.Notifications.route) {
                            launchSingleTop = true
                        }
                    },
                    onSwipeClick = {
                        navController.navigate(Screen.Swiping.route) {
                            launchSingleTop = true
                        }
                    },
                    onSwipeScreenClick = {
                        navController.navigate(Screen.Swiping.route) {
                            launchSingleTop = true
                        }
                    },
                    onChatListClick = {
                        navController.navigate(Screen.ChatList.route) {
                            launchSingleTop = true
                        }
                    },
                    onSchedulePlaydateClick = {
                        val matchId = "new" // or get from playdateViewModel
                        navController.navigate(Screen.Playdate.createRoute(matchId)) {
                            launchSingleTop = true
                        }
                    },
                    onChatClick = { chatId ->
                        navController.navigate(Screen.Chat.createRoute(chatId))
                    },
                    onPlaydateRequestsClick = {
                        // Since we're renaming Requests to Matches, this should navigate to Matches screen
                        navController.navigate(Screen.Matches.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

        }
    }
}



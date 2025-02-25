package io.pawsomepals.app


import SwipingScreen
import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.ui.screens.MatchesScreen
import io.pawsomepals.app.ui.screens.chat.ChatListScreen
import io.pawsomepals.app.ui.screens.chat.ChatScreen
import io.pawsomepals.app.ui.screens.playdate.components.LocationSearchScreen
import io.pawsomepals.app.ui.screens.playdate.components.PlaydateSchedulingScreen
import io.pawsomepals.app.ui.theme.AddEditDogProfileScreen
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
import io.pawsomepals.app.utils.CameraManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder


sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object TermsOfService : Screen("terms_of_service")
    object MainScreen : Screen("main_screen")
    object Questionnaire : Screen("questionnaire/{userId}/{dogId}") {
        fun createRoute(userId: String, dogId: String? = null): String {
            return "questionnaire/$userId/${dogId ?: "none"}"
        }
    }

    object Matches : Screen("matches") {
        fun createRoute() = route
    }
    object ChatPlaydateScheduling : Screen("chat/{chatId}/schedule_playdate") {
        fun createRoute(chatId: String) = "chat/${URLEncoder.encode(chatId, "UTF-8")}/schedule_playdate"
    }

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
        fun createRoute(matchId: String = "new") = "playdate/${URLEncoder.encode(matchId, "UTF-8")}"
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
    cameraManager: CameraManager,
    locationService: LocationSearchService,
    dataManager: DataManager,
    authStateManager: AuthStateManager,  // Add this

    lifecycleScope: LifecycleCoroutineScope



) {
    val navController = rememberNavController()
    val isUserFullyLoaded by authViewModel.isUserFullyLoaded.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val isSyncing by dataManager.isSyncing.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var isNavHostReady by remember { mutableStateOf(false) }


    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        android.util.Log.d("AppNavigation", "Current route: $currentRoute")
    }



    LaunchedEffect(Unit) {
        dataManager.startSync()
        Log.d("AppNavigation", "Initial Setup - AuthState: $authState")
        Log.d("AppNavigation", "Initial Setup - IsUserFullyLoaded: $isUserFullyLoaded")
        Log.d("AppNavigation", "Initial Setup - IsSyncing: $isSyncing")
    }

// Main navigation effect
    LaunchedEffect(authState, isUserFullyLoaded, isSyncing, isNavHostReady) {
        if (!isNavHostReady) return@LaunchedEffect

        withContext(Dispatchers.Main) {
            when {
                isUserFullyLoaded && authState is AuthStateManager.AuthState.Authenticated -> {
                    try {
                        dataManager.syncWithFirestore()

                        when (authState) {
                            is AuthStateManager.AuthState.Authenticated.NeedsTerms -> {
                                navController.navigate(Screen.TermsOfService.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                                Log.d(
                                    "AppNavigation",
                                    "User needs to accept TOS, navigating to TOS screen"
                                )
                            }

                            is AuthStateManager.AuthState.Authenticated.NeedsQuestionnaire -> {
                                val userId =
                                    (authState as AuthStateManager.AuthState.Authenticated.NeedsQuestionnaire).user.id
                                navController.navigate(Screen.Questionnaire.createRoute(userId)) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                                Log.d(
                                    "AppNavigation",
                                    "User needs questionnaire, navigating to questionnaire"
                                )
                            }

                            is AuthStateManager.AuthState.Authenticated.Complete -> {
                                if (!dataManager.isSyncing.value) {
                                    navController.navigate(Screen.MainScreen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                    Log.d(
                                        "AppNavigation",
                                        "User setup complete, navigating to main screen"
                                    )
                                }
                            }

                            else -> {
                                // Handle other Authenticated states if any
                                Log.d("AppNavigation", "Unhandled Authenticated state")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppNavigation", "Navigation failed", e)
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                }
                isUserFullyLoaded && authState is AuthStateManager.AuthState.Unauthenticated -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
                isUserFullyLoaded && authState is AuthStateManager.AuthState.Error -> {
                    Log.e(
                        "AppNavigation",
                        "Auth error: ${(authState as AuthStateManager.AuthState.Error).message}"
                    )
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
                isUserFullyLoaded && authState is AuthStateManager.AuthState.Initial -> {
                    Log.d("AppNavigation", "Waiting for auth state...")
                }
                else -> {
                    // Handle any other cases
                    Log.d("AppNavigation", "Unhandled state combination")
                }
            }
        }
    }

// Separate data sync effect
        LaunchedEffect(authState) {
            if (authState is AuthStateManager.AuthState.Authenticated) {
                try {
                    Log.d("AppNavigation", "Starting data sync")
                    dataManager.syncWithFirestore()
                    Log.d("AppNavigation", "Data sync completed successfully")
                } catch (e: Exception) {
                    Log.e("AppNavigation", "Error during data sync", e)
                }
            }
        }

// Main content
        if (isSyncing || !isUserFullyLoaded) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier.fillMaxSize()
            ) {

                composable(Screen.Splash.route) {
                    LaunchedEffect(Unit) {
                        isNavHostReady = true
                    }
                    if (currentUser != null && !isSyncing) {
                        SplashScreen(
                            authViewModel = authViewModel,
                            navController = navController,
                            cameraManager = cameraManager
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
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
                        onRegisterClick = { username, email, password, _, _ -> // Ignore confirmPassword and petName
                            authViewModel.registerUser(email, password, username)
                        },
                        onLoginClick = { navController.popBackStack() },
                        isLoading = authViewModel.isLoading.collectAsState().value,
                        errorMessage = authViewModel.errorMessage.collectAsState().value
                    )
                }

                composable(Screen.TermsOfService.route) {
                    val scope = rememberCoroutineScope()
                    TermsOfServiceScreen(
                        onAccept = {
                            scope.launch {
                                val userId =
                                    (authState as? AuthStateManager.AuthState.Authenticated)?.user?.id
                                if (userId != null) {
                                    authViewModel.updateUserTermsStatus(userId, true)
                                    navController.navigate(Screen.Questionnaire.createRoute(userId)) {
                                        popUpTo(Screen.TermsOfService.route) { inclusive = true }
                                    }
                                }
                            }
                        },
                        onDecline = {
                            scope.launch {
                                authViewModel.logOut()
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(
                    route = Screen.Profile.route,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val userId =
                        URLDecoder.decode(backStackEntry.arguments?.getString("userId"), "UTF-8")
                    ProfileScreen(
                        viewModel = profileViewModel,
                        dogProfileViewModel = dogProfileViewModel,
                        userId = userId,
                        cameraManager = cameraManager, // Updated parameter name and type
                        onBackClick = { navController.popBackStack() },
                        onNavigateToAddDog = {
                            navController.navigate(Screen.AddEditDogProfile.createRoute())
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
                    val chatId =
                        URLDecoder.decode(backStackEntry.arguments?.getString("chatId"), "UTF-8")
                    ChatScreen(
                        chatId = chatId,
                        viewModel = chatViewModel,
                        navController = navController,
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.ChatPlaydateScheduling.route,
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val chatId =
                        URLDecoder.decode(backStackEntry.arguments?.getString("chatId"), "UTF-8")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Call getMatchIdForChat and observe the result
                        LaunchedEffect(chatId) {
                            chatViewModel.getMatchIdForChat(chatId)
                        }

                        val matchId by chatViewModel.matchId.collectAsState()

                        // Show loading while fetching matchId
                        when (val currentMatchId = matchId) {
                            null -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }
                            }

                            else -> {
                                PlaydateSchedulingScreen(
                                    matchId = currentMatchId,
                                    onBack = { navController.popBackStack() },
                                    onComplete = {
                                        navController.popBackStack()
                                        // Optionally show success message or update chat
                                    }
                                )
                            }
                        }
                    } else {
                        // Show compatibility message for older Android versions
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Playdate scheduling requires Android 8.0 or higher",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                // Update the composable to not pass a request parameter
                // Replace the existing playdate composable in AppNavigation.kt with this:
                composable(
                    route = Screen.Playdate.route,
                    arguments = listOf(navArgument("matchId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val matchId =
                        URLDecoder.decode(backStackEntry.arguments?.getString("matchId"), "UTF-8")

                    // This is the button we're interested in - when matchId is "new"
                    if (matchId == "new") {
                        PlaydateSchedulingScreen(  // Change from PlaydateScreen to PlaydateSchedulingScreen
                            matchId = matchId,
                            onBack = { navController.popBackStack() },
                            onComplete = {
                                navController.popBackStack()
                            }
                        )
                    } else {
                        PlaydateSchedulingScreen(
                            matchId = matchId,
                            onBack = { navController.popBackStack() },
                            onComplete = {
                                navController.popBackStack()
                            }
                        )
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
                        swipingViewModel = swipingViewModel,
                        dogProfileViewModel = dogProfileViewModel,
                        profileViewModel = profileViewModel, // Add this
                        locationService = locationService,   // Add this
                        onNavigateToChat = { chatId ->
                            navController.navigate(Screen.Chat.createRoute(chatId))
                        },
                        onNavigateToPlaydate = { dogId ->
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
                // In ChatScreen
                composable(
                    route = "chat/{chatId}/location-search",
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                    LocationSearchScreen(
                        onLocationSelected = { location ->
                            chatViewModel.sendLocationMessage(location)
                            navController.popBackStack()
                        },
                        onDismiss = { navController.popBackStack() },
                        onBackPressed = { navController.popBackStack() }
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
                    val dogId =
                        backStackEntry.arguments?.getString("dogId")?.takeIf { it != "none" }
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
                composable(Screen.MainScreen.route) {
                    val scope = rememberCoroutineScope()
                    val navigateWithSingleTop = remember {
                        { route: Screen ->
                            navController.navigate(
                                when (route) {
                                    is Screen.Playdate -> route.createRoute("new")
                                    is Screen.Rating -> route.createRoute("new")
                                    is Screen.Profile -> route.createRoute("")
                                    else -> route.route
                                }
                            ) {
                                launchSingleTop = true
                            }
                        }
                    }

                    val username = remember(authState) {
                        when (authState) {
                            is AuthStateManager.AuthState.Authenticated -> (authState as AuthStateManager.AuthState.Authenticated).user.username
                            else -> ""
                        }
                    }

                    LaunchedEffect(Unit) {
                        scope.launch {
                            try {
                                dataManager.syncWithFirestore()
                            } catch (e: Exception) {
                                Log.e("MainScreen", "Initial sync failed", e)
                            }
                        }
                    }

                    when {
                        !isUserFullyLoaded || isSyncing -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        }

                        else -> {
                            val username by profileViewModel.username.collectAsState()

                            MainScreen(
                                navController = navController,
                                authStateManager = authStateManager,  // Add this
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
                                    navController.navigate(Screen.Profile.createRoute(userId))
                                },
                                onDogProfileClick = {
                                    navController.navigate(Screen.PlayfulDogProfile.createRoute())
                                },
                                onPlaydateClick = { playdateId ->
                                    navController.navigate(Screen.Playdate.createRoute(playdateId))
                                },
                                onChatClick = { chatId ->
                                    navController.navigate(Screen.Chat.createRoute(chatId))
                                },
                                onHealthAdvisorClick = {
                                    navigateWithSingleTop(Screen.HealthAdvisor)
                                },
                                onSettingsClick = {
                                    navigateWithSingleTop(Screen.Settings)
                                },
                                onPhotoManagementClick = {
                                    navigateWithSingleTop(Screen.PhotoManagement)
                                },
                                onRatingClick = {
                                    navigateWithSingleTop(Screen.Rating)
                                },
                                onNotificationsClick = {
                                    navigateWithSingleTop(Screen.Notifications)
                                },
                                onSwipeClick = {
                                    navigateWithSingleTop(Screen.Swiping)
                                },
                                onSwipeScreenClick = {
                                    navigateWithSingleTop(Screen.Swiping)
                                },
                                onChatListClick = {
                                    navigateWithSingleTop(Screen.ChatList)
                                },
                                onSchedulePlaydateClick = {
                                    navigateWithSingleTop(Screen.Playdate)
                                },
                                onPlaydateRequestsClick = {
                                    navigateWithSingleTop(Screen.Matches)
                                }
                            )
                        }
                    }

                }

            }
        }
    }



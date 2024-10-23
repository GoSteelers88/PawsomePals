package com.example.pawsomepals.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.pawsomepals.R
import com.example.pawsomepals.Screen
import com.example.pawsomepals.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    authViewModel: AuthViewModel,
    navController: NavController
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    LaunchedEffect(authState, currentUser) {
        delay(2000) // Optional: Add a small delay to show the splash screen

        when (authState) {
            AuthViewModel.AuthState.Authenticated -> {
                when {
                    currentUser == null -> navigateToLogin(navController)
                    !currentUser!!.hasAcceptedTerms -> navigateToTermsOfService(navController)
                    !currentUser!!.hasCompletedQuestionnaire -> navigateToQuestionnaire(navController)
                    else -> navigateToMainScreen(navController)
                }
            }
            AuthViewModel.AuthState.Unauthenticated -> navigateToLogin(navController)
            AuthViewModel.AuthState.Initial -> {} // Wait for auth state to be determined
        }
    }

    SplashScreenContent()
}

private fun navigateToLogin(navController: NavController) {
    navController.navigate(Screen.Login.route) {
        popUpTo(Screen.Splash.route) { inclusive = true }
    }
}

private fun navigateToTermsOfService(navController: NavController) {
    navController.navigate(Screen.TermsOfService.route) {
        popUpTo(Screen.Splash.route) { inclusive = true }
    }
}

private fun navigateToQuestionnaire(navController: NavController) {
    navController.navigate(Screen.Questionnaire.route) {
        popUpTo(Screen.Splash.route) { inclusive = true }
    }
}

private fun navigateToMainScreen(navController: NavController) {
    navController.navigate(Screen.MainScreen.route) {
        popUpTo(Screen.Splash.route) { inclusive = true }
    }
}

@Composable
private fun SplashScreenContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(150.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "PawsomePals",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator()
    }
}
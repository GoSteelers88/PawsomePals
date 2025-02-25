package io.pawsomepals.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.pawsomepals.app.R
import io.pawsomepals.app.Screen
import io.pawsomepals.app.utils.CameraManager
import io.pawsomepals.app.viewmodel.AuthViewModel
import kotlinx.coroutines.delay


@Composable
fun SplashScreen(
    authViewModel: AuthViewModel,
    navController: NavController,
    cameraManager: CameraManager
) {
    val authState by authViewModel.authState.collectAsState()
    val isUserFullyLoaded by authViewModel.isUserFullyLoaded.collectAsState()

    LaunchedEffect(Unit) {
        delay(2000) // Show splash for 2 seconds
        navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    SplashScreenContent()
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
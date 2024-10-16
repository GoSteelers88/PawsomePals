package com.example.pawsomepals

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.pawsomepals.ui.theme.LoginScreen
import com.example.pawsomepals.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authViewModel.handleGoogleSignInResult(result.data)
        } else {
            // Handle cancellation or error
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val googleSignInIntent by authViewModel.googleSignInIntent.collectAsState()
            val isLoading by authViewModel.isLoading.collectAsState()
            val errorMessage by authViewModel.errorMessage.collectAsState()

            LoginScreen(
                onLoginClick = { email, password -> authViewModel.loginUser(email, password) },
                onRegisterClick = { /* Navigate to register screen */ },
                onGoogleSignInClick = { authViewModel.beginGoogleSignIn() },
                onFacebookSignInClick = { authViewModel.beginFacebookSignIn() },
                onGoogleSignInIntentReceived = { intentSender ->
                    googleSignInLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                },
                googleSignInIntent = googleSignInIntent,
                isLoading = isLoading,
                errorMessage = errorMessage
            )
        }
    }
}
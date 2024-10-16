package com.example.pawsomepals.ui.theme

import android.content.IntentSender
import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pawsomepals.R
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onRegisterClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onFacebookSignInClick: () -> Unit,
    onGoogleSignInIntentReceived: (IntentSender) -> Unit,
    googleSignInIntent: IntentSender?,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isEmailError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var emailErrorMessage by remember { mutableStateOf("") }
    var passwordErrorMessage by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(googleSignInIntent) {
        googleSignInIntent?.let { intentSender ->
            onGoogleSignInIntentReceived(intentSender)
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && errorMessage == null) {
            showSuccessMessage = true
            delay(2000)
            showSuccessMessage = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to PawsomePals",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                isEmailError = false
                emailErrorMessage = ""
            },
            label = { Text("Email") },
            isError = isEmailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Email Input Field" },
            singleLine = true
        )
        if (isEmailError) {
            Text(
                text = emailErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                isPasswordError = false
                passwordErrorMessage = ""
            },
            label = { Text("Password") },
            isError = isPasswordError,
            visualTransformation = if (showPassword) PasswordVisualTransformation() else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Password Input Field" },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painter = painterResource(id = if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            }
        )
        if (isPasswordError) {
            Text(
                text = passwordErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    email.isEmpty() -> {
                        isEmailError = true
                        emailErrorMessage = "Email cannot be empty"
                    }
                    !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                        isEmailError = true
                        emailErrorMessage = "Invalid email format"
                    }
                    password.isEmpty() -> {
                        isPasswordError = true
                        passwordErrorMessage = "Password cannot be empty"
                    }
                    password.length < 6 -> {
                        isPasswordError = true
                        passwordErrorMessage = "Password must be at least 6 characters"
                    }
                    else -> onLoginClick(email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .semantics { contentDescription = "Login Button" },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Or sign in with",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onGoogleSignInClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .semantics { contentDescription = "Sign in with Google Button" },
            enabled = !isLoading
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Google Logo",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign in with Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onFacebookSignInClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .semantics { contentDescription = "Sign in with Facebook Button" },
            enabled = !isLoading
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_facebook_logo),
                contentDescription = "Facebook Logo",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign in with Facebook")
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = errorMessage != null || showSuccessMessage) {
            Text(
                text = if (showSuccessMessage) "Login Successful!" else errorMessage ?: "",
                color = if (showSuccessMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onRegisterClick,
            modifier = Modifier.semantics { contentDescription = "Register Button" }
        ) {
            Text("Don't have an account? Register")
        }
    }
}
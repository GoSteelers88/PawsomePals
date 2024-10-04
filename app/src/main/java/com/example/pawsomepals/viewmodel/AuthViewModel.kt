package com.example.pawsomepals.viewmodel

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.User
import com.example.pawsomepals.data.repository.AuthRepository
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.utils.RecaptchaManager
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val recaptchaManager: RecaptchaManager,
    private val facebookCallbackManager: CallbackManager,
    private val application: Application
) : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _signInIntent = MutableStateFlow<IntentSender?>(null)
    val signInIntent: StateFlow<IntentSender?> = _signInIntent.asStateFlow()

    private val _hasAcceptedTerms = MutableStateFlow(false)
    val hasAcceptedTerms: StateFlow<Boolean> = _hasAcceptedTerms.asStateFlow()

    private val _hasCompletedQuestionnaire = MutableStateFlow(false)
    val hasCompletedQuestionnaire: StateFlow<Boolean> = _hasCompletedQuestionnaire.asStateFlow()






    init {
        auth.currentUser?.let { firebaseUser ->
            viewModelScope.launch {
                _currentUser.value = userRepository.getUserByEmail(firebaseUser.email ?: "")
                _hasAcceptedTerms.value = _currentUser.value?.hasAcceptedTerms ?: false
                _hasCompletedQuestionnaire.value = _currentUser.value?.hasCompletedQuestionnaire ?: false
            }
        }
    }


    fun setQuestionnaireCompleted(completed: Boolean) {
        viewModelScope.launch {
            _hasCompletedQuestionnaire.value = completed
            // You might want to save this state to SharedPreferences or your backend
        }
    }


    fun loginUser(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val recaptchaVerified = recaptchaManager.executeRecaptcha("login")
                if (recaptchaVerified) {
                    val signInResult = authRepository.signInWithEmailAndPassword(email, password)
                    if (signInResult.isSuccess) {
                        val firebaseUser = signInResult.getOrThrow()
                        val user = createOrUpdateLocalUser(firebaseUser)
                        _currentUser.value = user
                        onSuccess()
                    } else {
                        val errorMsg = signInResult.exceptionOrNull()?.message ?: "Authentication failed"
                        _errorMessage.value = errorMsg
                        onError(errorMsg)
                    }
                } else {
                    val errorMsg = "reCAPTCHA verification failed"
                    _errorMessage.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email or password"
                    is FirebaseAuthInvalidUserException -> "No user found with this email"
                    else -> "Authentication failed: ${e.message}"
                }
                _errorMessage.value = errorMsg
                onError(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerUser(username: String, email: String, password: String, petName: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val recaptchaVerified = recaptchaManager.executeRecaptcha("register")
                if (recaptchaVerified) {
                    val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                    authResult.user?.let { firebaseUser ->
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()
                        firebaseUser.updateProfile(profileUpdates).await()

                        val user = createOrUpdateLocalUser(firebaseUser)
                        user.petName = petName
                        user.hasAcceptedTerms = false
                        user.hasCompletedQuestionnaire = false
                        userRepository.updateUser(user)
                        _currentUser.value = user
                        onSuccess()
                    } ?: run {
                        val errorMsg = "Failed to create user"
                        _errorMessage.value = errorMsg
                        onError(errorMsg)
                    }
                } else {
                    val errorMsg = "reCAPTCHA verification failed"
                    _errorMessage.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid email or password format"
                    is FirebaseAuthInvalidUserException -> "This email is already in use"
                    else -> "Registration failed: ${e.message}"
                }
                _errorMessage.value = errorMsg
                onError(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val idToken = account.idToken
        if (idToken != null) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    val user = createOrUpdateLocalUser(firebaseUser)
                    _currentUser.value = user
                } else {
                    _errorMessage.value = "Failed to retrieve user data"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Firebase authentication with Google failed: ${e.message}"
            }
        } else {
            _errorMessage.value = "Google Sign-In failed: No ID token"
        }
    }

    fun beginGoogleSignIn() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = authRepository.beginSignIn()
                if (result.isSuccess) {
                    _signInIntent.value = result.getOrNull()
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to begin Google Sign-In"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to begin Google Sign-In: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun handleGoogleSignInResult(data: Intent) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = authRepository.handleSignInResult(data)
                if (result.isSuccess) {
                    val firebaseUser = result.getOrNull()
                    firebaseUser?.let {
                        val user = createOrUpdateLocalUser(it)
                        _currentUser.value = user
                    } ?: run {
                        _errorMessage.value = "Failed to retrieve user data"
                    }
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Google Sign-In failed"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Google authentication failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setupFacebookCallback() {
        LoginManager.getInstance().registerCallback(facebookCallbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    handleFacebookAccessToken(result.accessToken.token)
                }

                override fun onCancel() {
                    _errorMessage.value = "Facebook login was cancelled"
                }

                override fun onError(error: FacebookException) {
                    _errorMessage.value = "Facebook login failed: ${error.message}"
                }
            })
    }

    private fun handleFacebookAccessToken(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val credential = FacebookAuthProvider.getCredential(token)
                val authResult = auth.signInWithCredential(credential).await()
                authResult.user?.let { firebaseUser ->
                    val user = createOrUpdateLocalUser(firebaseUser)
                    _currentUser.value = user
                } ?: run {
                    _errorMessage.value = "Failed to retrieve user data"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Facebook authentication failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateErrorMessage(error: String) {
        _errorMessage.value = error
    }

    fun acceptTerms() {
        viewModelScope.launch {
            _hasAcceptedTerms.value = true
            _currentUser.value?.let { user ->
                user.hasAcceptedTerms = true
                userRepository.updateUser(user)
            }
        }
    }

    fun submitQuestionnaire(answers: Map<String, String>) {
        viewModelScope.launch {
            _hasCompletedQuestionnaire.value = true
            _currentUser.value?.let { user ->
                user.hasCompletedQuestionnaire = true
                user.questionnaireAnswers = answers
                userRepository.updateUser(user)
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _currentUser.value = null
            _hasAcceptedTerms.value = false
            _hasCompletedQuestionnaire.value = false
            LoginManager.getInstance().logOut()
        }
    }

    private suspend fun createOrUpdateLocalUser(firebaseUser: FirebaseUser): User {
        val existingUser = userRepository.getUserByEmail(firebaseUser.email ?: "")
        return if (existingUser != null) {
            existingUser.apply {
                username = firebaseUser.displayName ?: username
                email = firebaseUser.email ?: email
                lastLoginTime = System.currentTimeMillis()
            }.also { userRepository.updateUser(it) }
        } else {
            User(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                password = "", // We don't store the password locally
                hasAcceptedTerms = false,
                hasCompletedQuestionnaire = false
            ).also { userRepository.insertUser(it) }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
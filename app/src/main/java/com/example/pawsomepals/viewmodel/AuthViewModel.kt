package com.example.pawsomepals.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
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
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.example.pawsomepals.auth.GoogleAuthManager
import com.example.pawsomepals.data.DataManager
import com.example.pawsomepals.data.model.Dog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.UUID

@HiltViewModel

class AuthViewModel @Inject constructor(
    private val dataManager: DataManager,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val recaptchaManager: RecaptchaManager,
    private val facebookCallbackManager: CallbackManager,
    @ApplicationContext private val context: Context,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    sealed class AuthState {
        object Initial : AuthState()
        object Authenticated : AuthState()
        object Unauthenticated : AuthState()
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()


    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isUserFullyLoaded = MutableStateFlow(false)
    val isUserFullyLoaded: StateFlow<Boolean> = _isUserFullyLoaded.asStateFlow()


    private val _googleSignInIntent = MutableStateFlow<Intent?>(null)
    val googleSignInIntent: StateFlow<Intent?> = _googleSignInIntent.asStateFlow()

    private val _hasAcceptedTerms = MutableStateFlow(false)
    val hasAcceptedTerms: StateFlow<Boolean> = _hasAcceptedTerms.asStateFlow()

    private val _hasCompletedQuestionnaire = MutableStateFlow(false)
    val hasCompletedQuestionnaire: StateFlow<Boolean> = _hasCompletedQuestionnaire.asStateFlow()

    init {
        viewModelScope.launch {
            Log.d("AuthViewModel", "Initializing AuthViewModel")

            // First check initial auth state
            auth.currentUser?.let { initialUser ->
                Log.d("AuthViewModel", "Found initial user: ${initialUser.uid}")
                handleAuthStateChange(initialUser)
            }

            // Set up auth state listener
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                Log.d("AuthViewModel", "Auth state changed. User: ${user?.uid}")

                viewModelScope.launch {
                    if (user != null) {
                        handleAuthStateChange(user)
                    } else {
                        handleSignOut()
                    }
                }
            }
        }
    }

    private suspend fun handleAuthStateChange(user: FirebaseUser) {
        try {
            _isLoading.value = true
            Log.d("AuthViewModel", "Handling auth state change for user: ${user.uid}")

            // Give Firestore a moment to complete the write if it's a new user
            delay(500)

            val dbUser = userRepository.getUserByEmail(user.email ?: "")
            if (dbUser != null) {
                Log.d("AuthViewModel", "Found user in database: ${dbUser.id}")
                _currentUser.value = dbUser
                _authState.value = AuthViewModel.AuthState.Authenticated
            } else {
                // Try one more time after a longer delay
                delay(1000)
                val retryUser = userRepository.getUserByEmail(user.email ?: "")
                if (retryUser != null) {
                    _currentUser.value = retryUser
                    _authState.value = AuthViewModel.AuthState.Authenticated
                } else {
                    Log.e("AuthViewModel", "User data not found after retry")
                    handleInvalidUser("User data not found")
                }
            }
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error handling auth state change", e)
            handleInvalidUser(e.message ?: "Unknown error occurred")
        } finally {
            _isLoading.value = false
        }
    }
    private suspend fun handleInvalidUser(reason: String) {
        Log.d("AuthViewModel", "Handling invalid user: $reason")
        _authState.value = AuthState.Unauthenticated
        _currentUser.value = null

        try {
            auth.signOut()
            Log.d("AuthViewModel", "User signed out due to: $reason")
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error signing out user", e)
        }
    }

    private fun handleSignOut() {
        Log.d("AuthViewModel", "Handling sign out")
        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated
        _isUserFullyLoaded.value = true
        _isLoading.value = false
    }
    fun getCurrentUserId(): String? {
        return currentUser.value?.id
    }
    fun isUserSetupComplete(): StateFlow<Boolean> = flow {
        val userId = getCurrentUserId()
        if (userId != null) {
            emit(userRepository.isUserSetupComplete(userId))
        } else {
            emit(false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    fun syncUserData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                dataManager.syncWithFirestore()
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to sync user data: ${e.message}"
                _isLoading.value = false
            }
        }
    }


    fun loginUser(email: String, password: String) {
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
                        _authState.value = AuthState.Authenticated
                        syncUserData() // Add this line
                    } else {
                        _errorMessage.value = signInResult.exceptionOrNull()?.message ?: "Authentication failed"
                    }
                } else {
                    _errorMessage.value = "reCAPTCHA verification failed"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Authentication failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerUser(username: String, email: String, password: String, petName: String?) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Create Firebase Auth user first
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw IllegalStateException("Failed to create user")

                // Create User object with empty dogIds
                val newUser = User(
                    id = firebaseUser.uid,
                    username = username,
                    email = email,
                    password = "",
                    dogIds = emptyList(),
                    hasAcceptedTerms = false,
                    hasCompletedQuestionnaire = false,
                    lastLoginTime = System.currentTimeMillis()
                )

                // Save user to Firestore and local DB
                userRepository.insertUser(newUser)

                // If petName is provided, create initial dog
                if (!petName.isNullOrBlank()) {
                    val dogId = UUID.randomUUID().toString()
                    val dog = Dog(
                        id = dogId,
                        ownerId = firebaseUser.uid,
                        name = petName
                    )
                    userRepository.insertDog(dog)

                    // Update user with dogId
                    val updatedUser = newUser.copy(dogIds = listOf(dogId))
                    userRepository.updateUser(updatedUser)
                }

                _currentUser.value = newUser
                _authState.value = AuthState.Authenticated

            } catch (e: Exception) {
                handleRegistrationError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    private fun handleRegistrationError(e: Exception) {
        Log.e("AuthViewModel", "Registration error", e)
        _errorMessage.value = when {
            e.message?.contains("email already in use", ignoreCase = true) == true ->
                "This email is already registered. Please try logging in instead."
            e.message?.contains("weak password", ignoreCase = true) == true ->
                "Password is too weak. Please use at least 6 characters."
            e.message?.contains("invalid email", ignoreCase = true) == true ->
                "Please enter a valid email address."
            else -> "Registration failed: ${e.message}"
        }

        // Clean up any partial registration
        viewModelScope.launch {
            try {
                auth.currentUser?.delete()?.await()
            } catch (cleanupError: Exception) {
                Log.e("AuthViewModel", "Error cleaning up failed registration", cleanupError)
            }
        }
    }
    fun beginGoogleSignIn() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = googleAuthManager.beginSignIn()
                if (result.isSuccess) {
                    _googleSignInIntent.value = result.getOrNull()
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
                val idTokenResult = googleAuthManager.handleSignInResult(data)
                if (idTokenResult.isSuccess) {
                    val idToken = idTokenResult.getOrNull()
                    if (idToken != null) {
                        val firebaseAuthResult = googleAuthManager.firebaseAuthWithGoogle(idToken)
                        if (firebaseAuthResult.isSuccess) {
                            val firebaseUser = firebaseAuthResult.getOrNull()
                            if (firebaseUser != null) {
                                handleFirebaseUser(firebaseUser)
                            } else {
                                _errorMessage.value = "Failed to get user data after authentication"
                            }
                        } else {
                            _errorMessage.value = firebaseAuthResult.exceptionOrNull()?.message ?: "Firebase authentication failed"
                        }
                    } else {
                        _errorMessage.value = "No ID token found"
                    }
                } else {
                    _errorMessage.value = idTokenResult.exceptionOrNull()?.message ?: "Failed to get ID token"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Google Sign-In failed: ${e.message}"
                Log.e("AuthViewModel", "Google Sign-In error", e)
            } finally {
                _isLoading.value = false
                _googleSignInIntent.value = null
            }
        }
    }


    fun beginFacebookSignIn() {
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
        // This line needs to be called from an Activity or Fragment
        // LoginManager.getInstance().logInWithReadPermissions(activity, listOf("email", "public_profile"))
    }

    fun handleFacebookAccessToken(token: String) {
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


    fun acceptTerms() {
        viewModelScope.launch {
            _hasAcceptedTerms.value = true
            _currentUser.value?.let { user ->
                user.hasAcceptedTerms = true
                userRepository.updateUser(user)
            }
        }
    }

    fun setQuestionnaireCompleted(completed: Boolean) {
        viewModelScope.launch {
            _hasCompletedQuestionnaire.value = completed
            _currentUser.value?.let { user ->
                user.hasCompletedQuestionnaire = completed
                userRepository.updateUser(user)
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            try {
                // First stop any ongoing operations
                dataManager.cleanup()

                // Clear local data
                withContext(Dispatchers.IO) {
                    dataManager.clearAllLocalData()
                }

                // Sign out from Google if used
                googleAuthManager.signOut()

                // Update states
                _currentUser.value = null
                _authState.value = AuthViewModel.AuthState.Unauthenticated

                // Finally, sign out from Firebase
                auth.signOut()

                Log.d("AuthViewModel", "User logged out successfully")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to log out: ${e.message}"
                Log.e("AuthViewModel", "Error during logout", e)
            }
        }
    }

    private suspend fun createOrUpdateLocalUser(firebaseUser: FirebaseUser): User {
        val existingUser = userRepository.getUserByEmail(firebaseUser.email ?: "")
        return existingUser?.apply {
            username = firebaseUser.displayName ?: username
            email = firebaseUser.email ?: email
            lastLoginTime = System.currentTimeMillis()
        }?.also { userRepository.updateUser(it) }
            ?: User(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                password = "",
                dogIds = emptyList(),  // Initialize empty dog list
                hasAcceptedTerms = false,
                hasCompletedQuestionnaire = false
            ).also { userRepository.insertUser(it) }
    }


    private suspend fun handleFirebaseUser(firebaseUser: FirebaseUser) {
        try {
            val existingUser = userRepository.getUserByEmail(firebaseUser.email ?: "")
            val user = // User exists, update the user data
                existingUser?.copy(
                    username = firebaseUser.displayName ?: existingUser.username,
                    email = firebaseUser.email ?: existingUser.email,
                    lastLoginTime = System.currentTimeMillis()
                )?.also {
                    userRepository.updateUser(it)
                    Log.d("AuthViewModel", "Existing user updated and authenticated: ${it.id}")
                }
                    ?: // User doesn't exist in Firestore, create a new user
                    User(
                        id = firebaseUser.uid,
                        username = firebaseUser.displayName ?: "",
                        email = firebaseUser.email ?: "",
                        hasAcceptedTerms = false,
                        hasCompletedQuestionnaire = false
                    ).also {
                        userRepository.insertUser(it)
                        Log.d("AuthViewModel", "New user created and authenticated: ${it.id}")
                    }
            _currentUser.value = user
            _authState.value = AuthState.Authenticated
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Error handling Firebase user", e)
            _errorMessage.value = "Error updating user data. Some features may be limited."
            // Still set the user as authenticated, even if Firestore update fails
            _currentUser.value = User(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                hasAcceptedTerms = false,
                hasCompletedQuestionnaire = false
            )
            _authState.value = AuthState.Authenticated
        }
    }


    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

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

            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                Log.d("AuthViewModel", "Auth state changed. User: ${user?.uid}")
                if (user != null) {
                    viewModelScope.launch {
                        try {
                            _isLoading.value = true
                            val dbUser = userRepository.getUserByEmail(user.email ?: "")
                            if (dbUser != null) {
                                _currentUser.value = dbUser
                                _authState.value = AuthState.Authenticated
                            } else {
                                // User exists in Firebase but not in Firestore
                                _authState.value = AuthState.Unauthenticated
                                auth.signOut() // Sign out the user
                            }
                        } catch (e: Exception) {
                            Log.e("AuthViewModel", "Error fetching user data: ${e.message}")
                            _errorMessage.value = "Failed to load user data"
                            _authState.value = AuthState.Unauthenticated
                        } finally {
                            _isLoading.value = false
                            _isUserFullyLoaded.value = true
                        }
                    }
                } else {
                    _currentUser.value = null
                    _authState.value = AuthState.Unauthenticated
                    _isUserFullyLoaded.value = true
                    _isLoading.value = false
                }
            }
        }
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
                dataManager.syncWithFirebase()
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
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val recaptchaVerified = recaptchaManager.executeRecaptcha("register")
                if (recaptchaVerified) {
                    val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                    authResult.user?.let { firebaseUser ->
                        val user = createOrUpdateLocalUser(firebaseUser)
                        user.username = username
                        user.petName = petName
                        user.hasAcceptedTerms = false
                        user.hasCompletedQuestionnaire = false
                        userRepository.updateUser(user)
                        _currentUser.value = user
                    } ?: run {
                        _errorMessage.value = "Failed to create user"
                    }
                } else {
                    _errorMessage.value = "reCAPTCHA verification failed"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Registration failed: ${e.message}"
            } finally {
                _isLoading.value = false
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
                googleAuthManager.signOut()
                dataManager.clearAllLocalData()
                _currentUser.value = null
                _authState.value = AuthState.Unauthenticated
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
                password = "", // We don't store the password locally
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
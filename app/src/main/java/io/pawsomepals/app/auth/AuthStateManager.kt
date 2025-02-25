package io.pawsomepals.app.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthStateManager"


@Singleton
class AuthStateManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val firestore: FirebaseFirestore
) {

    sealed class AuthState {
        object Initial : AuthState()
        object Unauthenticated : AuthState()

        sealed class Authenticated(val user: User) : AuthState() {
            class NeedsTerms(user: User) : Authenticated(user)
            class NeedsQuestionnaire(user: User) : Authenticated(user)
            class Complete(user: User) : Authenticated(user)
        }

        class Error(val exception: Exception) : AuthState() {
            val message: String = exception.message ?: "Unknown error occurred"
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isUserFullyLoaded = MutableStateFlow(false)
    val isUserFullyLoaded: StateFlow<Boolean> = _isUserFullyLoaded.asStateFlow()

    init {
        // Start fresh by signing out
        setupAuthStateListener()
    }
    private fun setupAuthStateListener() {
        auth.addAuthStateListener { firebaseAuth ->
            android.util.Log.d(TAG, "Auth state changed. Current user: ${firebaseAuth.currentUser?.uid}")
            scope.launch {
                try {
                    handleAuthStateChange(firebaseAuth.currentUser)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error in auth state listener", e)
                }
            }
        }
    }
    fun resetState() {
        _authState.value = AuthState.Initial
        _isUserFullyLoaded.value = false
        _isLoading.value = false
    }


    private suspend fun handleAuthStateChange(firebaseUser: FirebaseUser?) {
        try {
            _isUserFullyLoaded.value = false

            if (firebaseUser == null) {
                _authState.value = AuthState.Unauthenticated
                _isUserFullyLoaded.value = true
                _username.value = ""
                return
            }

            // Add explicit logging
            Log.d(TAG, "Handling auth state change for user: ${firebaseUser.uid}")

            val user = try {
                getUserData(firebaseUser).also {
                    Log.d(TAG, "User data retrieved: terms=${it.hasAcceptedTerms}, questionnaire=${it.hasCompletedQuestionnaire}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user data, creating new user", e)
                createNewUser(firebaseUser)
            }

            _username.value = determineUsername(user, firebaseUser)
            updateAuthStateBasedOnUser(user)

        } catch (e: Exception) {
            Log.e(TAG, "Error in handleAuthStateChange", e)
            _authState.value = AuthState.Error(e)
        } finally {
            _isUserFullyLoaded.value = true
        }
    }
    private fun updateAuthStateBasedOnUser(user: User) {
        _authState.value = when {
            !user.hasAcceptedTerms -> AuthState.Authenticated.NeedsTerms(user)
            !user.hasCompletedQuestionnaire -> AuthState.Authenticated.NeedsQuestionnaire(user)
            else -> AuthState.Authenticated.Complete(user)
        }
    }
    private fun determineUsername(user: User, firebaseUser: FirebaseUser): String {
        return when {
            user.username.isNotBlank() -> user.username  // Prioritize username
            user.firstName?.isNotBlank() == true -> user.firstName!!
            firebaseUser.displayName?.isNotBlank() == true -> firebaseUser.displayName!!
            firebaseUser.email?.isNotBlank() == true -> firebaseUser.email!!.substringBefore('@')
            else -> "User"
        }
    }
    suspend fun signInWithEmail(email: String, password: String) {
        android.util.Log.d(TAG, "Starting signInWithEmail")
        _isLoading.value = true
        try {
            _isUserFullyLoaded.value = false  // Add this
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw IllegalStateException("Failed to get user after login")

            val userData = getUserData(firebaseUser)
            android.util.Log.d(TAG, "Got user data: $userData")
            updateAuthState(userData)

            _isUserFullyLoaded.value = true  // Add this
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Login failed", e)
            _authState.value = AuthState.Error(e)
            _isUserFullyLoaded.value = true  // Add this for error case
        } finally {
            _isLoading.value = false
        }
    }
    suspend fun createUserWithEmail(email: String, password: String, username: String) {
        _isLoading.value = true
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw IllegalStateException("User creation failed")

            val user = User(
                id = firebaseUser.uid,
                email = email,
                username = username
            )

            userRepository.insertUser(user)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signOut() {
        _isLoading.value = true
        try {
            auth.signOut()
            _authState.value = AuthState.Unauthenticated
            _isUserFullyLoaded.value = false
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun getUserData(firebaseUser: FirebaseUser): User {
        try {
            // 1. Check local database
            var user = userRepository.getUserById(firebaseUser.uid)
            if (user != null) {
                return user
            }

            // 2. Check Firestore if not found locally
            val firestoreUser = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()
                .toObject(User::class.java)

            if (firestoreUser != null) {
                // Save to local DB and return
                userRepository.insertUser(firestoreUser)
                return firestoreUser
            }

            // 3. Only create new user if not found in either location
            return createNewUser(firebaseUser)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting user data", e)
            throw e
        }
    }

    private suspend fun createNewUser(firebaseUser: FirebaseUser): User {
        android.util.Log.d(TAG, "Creating new user for ${firebaseUser.uid}")
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            username = firebaseUser.displayName ?: "",
            hasAcceptedTerms = false,
            hasCompletedQuestionnaire = false
        ).also {
            try {
                userRepository.insertUser(it)
                android.util.Log.d(TAG, "New user created and inserted: $it")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to insert new user", e)
                throw e
            }
        }
    }
    suspend fun verifyUserSetup(userId: String) {
        try {
            _isLoading.value = true

            // Check both local and Firestore
            val localUser = userRepository.getUserById(userId)
            val firestoreUser = firestore.collection("users")
                .document(userId)
                .get()
                .await()
                .toObject(User::class.java)

            // Use Firestore data if available and newer
            val user = when {
                firestoreUser?.lastUpdated ?: 0 > localUser?.lastUpdated ?: 0 -> firestoreUser
                else -> localUser
            }

            if (user != null) {
                // Sync data if needed
                if (localUser != firestoreUser) {
                    user.let { userRepository.insertUser(it) }
                }

                updateAuthStateBasedOnUser(user)
            } else {
                _authState.value = AuthState.Error(Exception("User data not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying user setup", e)
            _authState.value = AuthState.Error(e)
        } finally {
            _isLoading.value = false
        }
    }
    suspend fun updateUserTerms(userId: String, accepted: Boolean) {
        try {
            _isLoading.value = true

            // Get current user data
            val user = userRepository.getUserById(userId)?.copy(
                hasAcceptedTerms = accepted,
                lastUpdated = System.currentTimeMillis()
            ) ?: throw Exception("User not found")

            // Update local database
            userRepository.insertUser(user)

            // Update Firestore
            firestore.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "hasAcceptedTerms" to accepted,
                        "lastUpdated" to user.lastUpdated
                    )
                )
                .await()

            // Refresh auth state
            updateAuthStateBasedOnUser(user)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating terms acceptance", e)
            _authState.value = AuthState.Error(e)
        } finally {
            _isLoading.value = false
        }
    }
    suspend fun updateUsername(newUsername: String) {
        try {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("No authenticated user")

            // Update Firestore
            firestore.collection("users")
                .document(userId)
                .update("username", newUsername)
                .await()

            // Update local state
            _username.value = newUsername

            // Update user in repository
            userRepository.getUserById(userId)?.let { currentUser ->
                val updatedUser = currentUser.copy(
                    username = newUsername,
                    lastUpdated = System.currentTimeMillis()
                )
                userRepository.insertUser(updatedUser)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating username", e)
            throw e
        }
    }





    private fun updateAuthState(user: User) {
        val newState = when {
            !user.hasAcceptedTerms -> {
                android.util.Log.d(TAG, "User needs to accept terms")
                AuthState.Authenticated.NeedsTerms(user)
            }
            !user.hasCompletedQuestionnaire -> {
                android.util.Log.d(TAG, "User needs to complete questionnaire")
                AuthState.Authenticated.NeedsQuestionnaire(user)
            }
            else -> {
                android.util.Log.d(TAG, "User setup is complete")
                AuthState.Authenticated.Complete(user)
            }
        }
        _authState.value = newState
    }
    fun clearAuthState() {
        auth.signOut()
        _authState.value = AuthState.Initial
        _isUserFullyLoaded.value = false
        _isLoading.value = false
    }
}
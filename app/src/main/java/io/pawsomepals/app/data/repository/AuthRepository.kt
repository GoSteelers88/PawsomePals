package io.pawsomepals.app.data.repository

import android.content.Context
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.R
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.exception.UserAlreadyExistsException
import io.pawsomepals.app.utils.RecaptchaManager
import io.pawsomepals.app.utils.retryWithExponentialBackoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val recaptchaManager: RecaptchaManager,
    private val authStateManager: AuthStateManager,
    @ApplicationContext private val context: Context
) {
    private val oneTapClient: SignInClient = Identity.getSignInClient(context)

    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw IllegalStateException("Failed to get user after login")
                Result.success(user)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Login failed", e)
                Result.failure(e)
            }
        }
    }
    suspend fun registerWithEmail(email: String, password: String, username: String): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if user exists
                val existingUser = auth.fetchSignInMethodsForEmail(email).await()
                if (!existingUser.signInMethods.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("User already exists"))
                }

                // Create Firebase Auth user
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw IllegalStateException("Failed to create user")

                // Create Firestore user document
                val user = User(
                    id = firebaseUser.uid,
                    username = username,
                    email = email
                )

                // Save to Firestore
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(user)
                    .await()

                Result.success(firebaseUser)
            } catch (e: Exception) {
                // Clean up if creation fails
                auth.currentUser?.delete()?.await()
                Result.failure(e)
            }
        }
    }
    fun clearAuthState() {
        auth.signOut()
        oneTapClient.signOut() // Clear Google Sign-In state
        authStateManager.resetState()
        Log.d("AuthRepository", "Auth state cleared on app exit")
    }

    suspend fun beginSignIn(): Result<IntentSender> {
        return withContext(Dispatchers.IO) {
            try {
                val signInRequest = BeginSignInRequest.builder()
                    .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                            .setSupported(true)
                            .setServerClientId(context.getString(R.string.default_web_client_id))
                            .setFilterByAuthorizedAccounts(false)  // This ensures all accounts are shown
                            .build()
                    )
                    .setAutoSelectEnabled(false)  // This disables auto-select
                    .build()

                val result = oneTapClient.beginSignIn(signInRequest).await()
                Result.success(result.pendingIntent.intentSender)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error in beginSignIn", e)
                Result.failure(e)
            }
        }
    }
    suspend fun createUserInFirestore(email: String, password: String, username: String, petName: String?): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                // First check if user exists in Firebase Auth
                val existingUser = auth.fetchSignInMethodsForEmail(email).await()
                if (!existingUser.signInMethods.isNullOrEmpty()) {
                    return@withContext Result.failure(UserAlreadyExistsException("User already exists"))
                }

                // Create Firebase Auth user
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw IllegalStateException("Failed to create user")

                // Create Firestore user document
                val user = User(
                    id = firebaseUser.uid,
                    username = username,
                    email = email,
                    // ... other user properties
                )

                // Save to Firestore in transaction
                firestore.runTransaction { transaction ->
                    val userRef = firestore.collection("users").document(firebaseUser.uid)
                    transaction.set(userRef, user)
                }.await()

                Result.success(firebaseUser)
            } catch (e: Exception) {
                // Clean up if Firestore save fails
                try {
                    auth.currentUser?.delete()?.await()
                } catch (e: Exception) {
                    Log.e("AuthRepository", "Error cleaning up auth user", e)
                }
                Result.failure(e)
            }
        }
    }
    suspend fun saveUserToFirestore(firebaseUser: FirebaseUser) {
        try {
            val user = User(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: ""
            ).apply {
                lastLoginTime = System.currentTimeMillis()
                profilePictureUrl = firebaseUser.photoUrl?.toString()
            }

            retryWithExponentialBackoff {
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(user)
                    .await()
            }
            Log.d("AuthRepository", "User saved to Firestore successfully")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error saving user to Firestore", e)
            throw e
        }
    }
    suspend fun handleSignInResult(data: android.content.Intent): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken
                when {
                    idToken != null -> {
                        Log.d("AuthRepository", "Got ID token")
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = retryWithExponentialBackoff {
                            auth.signInWithCredential(firebaseCredential).await()
                        }
                        val user = authResult.user!!
                        saveUserToFirestore(user)
                        Log.d("AuthRepository", "handleSignInResult successful")
                        Result.success(user)
                    }
                    else -> {
                        Log.e("AuthRepository", "No ID token!")
                        Result.failure(Exception("No ID token!"))
                    }
                }
            } catch (e: ApiException) {
                Log.e("AuthRepository", "ApiException in handleSignInResult", e)
                Result.failure(e)
            }
        }
    }




    suspend fun deleteAccount(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val user = auth.currentUser
                user?.let {
                    firestore.collection("users").document(it.uid).delete().await()
                    it.delete().await()
                }
                auth.signOut()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }


    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                clearAuthState()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error signing out", e)
                throw e
            }
        }
    }
}
package io.pawsomepals.app.data.repository

import android.content.Context
import android.content.IntentSender
import android.util.Log
import io.pawsomepals.app.R
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.utils.RecaptchaManager
import io.pawsomepals.app.utils.retryWithExponentialBackoff
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.safetynet.SafetyNet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context
) {
    private val oneTapClient: SignInClient = Identity.getSignInClient(context)

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
                Log.d("AuthRepository", "Starting user creation process for: $email")

                // 1. Create Firebase Auth user
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw IllegalStateException("Failed to create user")

                // 2. Create complete User object
                val user = User(
                    id = firebaseUser.uid,
                    username = username,
                    email = email,
                    password = "", // Don't store actual password
                    firstName = null,
                    lastName = null,
                    bio = null,
                    profilePictureUrl = null,
                    dogIds = emptyList(), // Add this line for the new dogIds field
                    hasAcceptedTerms = false,
                    hasCompletedQuestionnaire = false,
                    latitude = null,
                    longitude = null,
                    lastLoginTime = System.currentTimeMillis(),
                    hasSubscription = false,
                    subscriptionEndDate = null,
                    dailyQuestionCount = 0,
                    phoneNumber = null,
                    preferredContact = null,
                    notificationsEnabled = true
                )

                // 3. Save to Firestore
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(user)
                    .await()

                Log.d("AuthRepository", "User created successfully in both Auth and Firestore")
                Result.success(firebaseUser)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error creating user", e)
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

    suspend fun verifyRecaptcha(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = SafetyNet.getClient(context)
                    .verifyWithRecaptcha("6LdYYykqAAAAANO5lp4COf55lFzDcZCimr7P7IHe")
                    .await()
                if (response.tokenResult.isNullOrEmpty()) {
                    Result.failure(Exception("reCAPTCHA verification failed"))
                } else {
                    Result.success(true)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                val result = retryWithExponentialBackoff {
                    auth.signInWithEmailAndPassword(email, password).await()
                }
                result.user?.let {
                    saveUserToFirestore(it)
                    Log.d("AuthRepository", "signInWithEmailAndPassword successful")
                    Result.success(it)
                } ?: run {
                    Log.e("AuthRepository", "Authentication failed: User is null")
                    Result.failure(Exception("Authentication failed: User is null"))
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error in signInWithEmailAndPassword", e)
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
                oneTapClient.signOut().await()
                auth.signOut()
                // Clear any local user data if necessary
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error during sign out", e)
            }
        }
    }
    suspend fun signOutGoogle() {
        withContext(Dispatchers.IO) {
            try {
                oneTapClient.signOut().await()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error signing out from Google", e)
            }
        }
    }

    suspend fun firebaseAuthWithGoogle(idToken: String): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = result.user!!
                saveUserToFirestore(user)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun saveUserToFirestore(user: FirebaseUser) {
        val userMap = hashMapOf(
            "uid" to user.uid,
            "email" to user.email,
            "displayName" to user.displayName,
            "photoUrl" to user.photoUrl?.toString()
        )
        try {
            retryWithExponentialBackoff {
                firestore.collection("users").document(user.uid).set(userMap).await()
            }
            Log.d("AuthRepository", "User saved to Firestore successfully")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error saving user to Firestore", e)
        }
    }
}
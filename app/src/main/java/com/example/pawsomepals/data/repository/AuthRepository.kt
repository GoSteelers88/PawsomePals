package com.example.pawsomepals.data.repository

import android.content.Context
import android.content.IntentSender
import com.example.pawsomepals.utils.RecaptchaManager
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.safetynet.SafetyNet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
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
                            .setServerClientId(context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName)))
                            .setFilterByAuthorizedAccounts(true)
                            .build())
                    .build()

                val result = oneTapClient.beginSignIn(signInRequest).await()
                Result.success(result.pendingIntent.intentSender)
            } catch (e: Exception) {
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
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = auth.signInWithCredential(firebaseCredential).await()
                        Result.success(authResult.user!!)
                    }
                    else -> {
                        Result.failure(Exception("No ID token!"))
                    }
                }
            } catch (e: ApiException) {
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
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Authentication failed: User is null"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val user = auth.currentUser
                user?.delete()?.await()
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
            } catch (e: Exception) {
                // Handle sign out error
            }
        }
    }

    suspend fun firebaseAuthWithGoogle(idToken: String): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                Result.success(result.user!!)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }}
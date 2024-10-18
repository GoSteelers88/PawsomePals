package com.example.pawsomepals.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val signInClient: SignInClient = Identity.getSignInClient(context)
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(com.example.pawsomepals.R.string.default_web_client_id))
        .requestEmail()
        .build()

    suspend fun beginSignIn(): Result<Intent> {
        return try {
            val signInIntent = GoogleSignIn.getClient(context, gso).signInIntent
            Result.success(signInIntent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun handleSignInResult(data: Intent): Result<String> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                Result.success(idToken)
            } else {
                Result.failure(Exception("No ID token found"))
            }
        } catch (e: ApiException) {
            Result.failure(e)
        }
    }

    suspend fun firebaseAuthWithGoogle(idToken: String): Result<Unit> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            firebaseAuth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            GoogleSignIn.getClient(context, gso).signOut().await()
            firebaseAuth.signOut()
        } catch (e: Exception) {
            // Handle sign out error
        }
    }
}
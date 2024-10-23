package com.example.pawsomepals.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class GoogleAuthManager @Inject constructor(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(com.example.pawsomepals.R.string.default_web_client_id))
        .requestEmail()
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, gso)

    suspend fun beginSignIn(): Result<Intent> {
        return try {
            val signInIntent = googleSignInClient.signInIntent
            Result.success(signInIntent)
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Error beginning sign-in", e)
            Result.failure(e)
        }
    }

    suspend fun handleSignInResult(data: Intent): Result<String> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                Log.d("GoogleAuthManager", "Got ID token: ${idToken.take(10)}...")
                Result.success(idToken)
            } else {
                Log.e("GoogleAuthManager", "No ID token found in credentials")
                Result.failure(Exception("No ID token found"))
            }
        } catch (e: ApiException) {
            Log.e("GoogleAuthManager", "Error handling sign-in result", e)
            Result.failure(e)
        }
    }

    suspend fun firebaseAuthWithGoogle(idToken: String): Result<FirebaseUser> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                Log.d("GoogleAuthManager", "Firebase auth successful, user: ${user.uid}")
                Result.success(user)
            } else {
                Log.e("GoogleAuthManager", "Firebase auth successful but user is null")
                Result.failure(Exception("Firebase auth successful but user is null"))
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Error authenticating with Firebase", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            GoogleSignIn.getClient(context, gso).signOut().await()
            firebaseAuth.signOut()
            Log.d("GoogleAuthManager", "Sign out successful")
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Error signing out", e)
            // Handle sign out error
        }
    }
}
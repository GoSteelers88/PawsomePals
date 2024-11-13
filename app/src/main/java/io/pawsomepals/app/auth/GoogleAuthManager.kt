package io.pawsomepals.app.auth

import android.content.Context
import android.content.Intent
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

@Singleton
class GoogleAuthManager @Inject constructor(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    companion object {
        private const val TAG = "GoogleAuthManager"
    }

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(io.pawsomepals.app.R.string.default_web_client_id))
        .requestEmail()
        .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/calendar"))
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, gso)

    suspend fun getSignInIntent(): Result<Intent> {
        return try {
            val signInIntent = googleSignInClient.signInIntent
            Result.success(signInIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sign in intent", e)
            Result.failure(e)
        }
    }

    suspend fun handleSignInResult(data: Intent): Result<String> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                Log.d(TAG, "Got ID token: ${idToken.take(10)}...")
                Result.success(idToken)
            } else {
                Log.e(TAG, "No ID token found in credentials")
                Result.failure(Exception("No ID token found"))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Error handling sign-in result", e)
            Result.failure(e)
        }
    }

    suspend fun firebaseAuthWithGoogle(idToken: String): Result<FirebaseUser> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                Log.d(TAG, "Firebase auth successful, user: ${user.uid}")
                Result.success(user)
            } else {
                Log.e(TAG, "Firebase auth successful but user is null")
                Result.failure(Exception("Firebase auth successful but user is null"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error authenticating with Firebase", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
            firebaseAuth.signOut()
            Log.d(TAG, "Sign out successful")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }
}
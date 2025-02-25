package io.pawsomepals.app.auth

import com.google.firebase.auth.FirebaseUser

interface AuthManager {
    suspend fun startPeriodicSync()
    suspend fun stopPeriodicSync()
    suspend fun clearAllLocalData()
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser>
    suspend fun cleanup()
}

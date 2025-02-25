package io.pawsomepals.app.data

import com.google.firebase.auth.FirebaseUser

interface DataManagerDelegate {
    suspend fun startPeriodicSync()
    suspend fun stopPeriodicSync()
    suspend fun clearAllLocalData()
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser>
    suspend fun cleanup()
}
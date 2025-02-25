package io.pawsomepals.app.auth

import com.google.firebase.auth.FirebaseUser
import io.pawsomepals.app.data.DataManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationDelegateImpl @Inject constructor(
    private val dataManager: Lazy<DataManager>
) : AuthenticationDelegate {
    override suspend fun startPeriodicSync() {
        dataManager.value.startPeriodicSync()
    }

    override suspend fun stopPeriodicSync() {
        dataManager.value.stopPeriodicSync()
    }

    override suspend fun clearAllLocalData() {
        dataManager.value.clearAllLocalData()
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return dataManager.value.signInWithEmailAndPassword(email, password)
    }

    override suspend fun cleanup() {
        dataManager.value.cleanup()
    }
}
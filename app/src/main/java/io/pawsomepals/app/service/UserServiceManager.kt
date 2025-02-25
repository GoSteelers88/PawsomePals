package io.pawsomepals.app.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.dao.DogDao
import io.pawsomepals.app.data.dao.MatchDao
import io.pawsomepals.app.data.dao.UserDao
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserServiceManager@Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val dogDao: DogDao,
    private val matchDao: MatchDao,
    @ApplicationScope private val scope: CoroutineScope
){
    private var serviceJob: Job? = null
    private val _userServices = mutableMapOf<String, Job>()
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)

    sealed class ServiceState {
        object Idle : ServiceState()
        object Running : ServiceState()
        data class Error(val message: String) : ServiceState()
    }

    fun initializeForUser(userId: String) {
        serviceJob = scope.launch {
            try {
                _serviceState.value = ServiceState.Running
                startUserServices(userId)
                monitorAuthState()
            } catch (e: Exception) {
                Log.e("UserServiceManager", "Error initializing services", e)
                _serviceState.value = ServiceState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun startUserServices(userId: String) {
        _userServices["dataSyncService"] = scope.launch {
            try {
                syncUserData(userId)
            } catch (e: Exception) {
                Log.e("UserServiceManager", "Data sync service failed", e)
            }
        }

        _userServices["matchMonitorService"] = scope.launch {
            try {
                monitorUserMatches(userId)
            } catch (e: Exception) {
                Log.e("UserServiceManager", "Match monitor service failed", e)
            }
        }
    }

    private suspend fun syncUserData(userId: String) {
        try {
            val firestoreUser = firestore.collection("users")
                .document(userId)
                .get()
                .await()
                .toObject(User::class.java)

            firestoreUser?.let { user ->
                userDao.insertUser(user)

                val dogs = firestore.collection("dogs")
                    .whereEqualTo("ownerId", userId)
                    .get()
                    .await()
                    .toObjects(Dog::class.java)

                dogs.forEach { dog ->
                    dogDao.insertDog(dog)
                }
            }
        } catch (e: Exception) {
            Log.e("UserServiceManager", "Error syncing user data", e)
            throw e
        }
    }

    private fun monitorUserMatches(userId: String) {
        firestore.collection("matches")
            .whereEqualTo("user1Id", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("UserServiceManager", "Error monitoring matches", error)
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    scope.launch {
                        querySnapshot.toObjects(Match::class.java).forEach { match ->
                            matchDao.insertMatch(match)
                        }
                    }
                }
            }
    }

    private fun monitorAuthState() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                reset()
            }
        }
    }

    fun reset() {
        serviceJob?.cancel()
        _userServices.values.forEach { it.cancel() }
        _userServices.clear()
        _serviceState.value = ServiceState.Idle
    }

    fun cleanup() {
        reset()
        scope.cancel()
    }
}
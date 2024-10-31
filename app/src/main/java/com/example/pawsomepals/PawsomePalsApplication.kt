package com.example.pawsomepals

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.pawsomepals.data.DataManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PawsomePalsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var dataManager: DataManager

    companion object {
        lateinit var instance: PawsomePalsApplication
            private set
        private const val MAX_AUTH_RETRIES = 3
        private const val AUTH_RETRY_DELAY = 2000L // 2 seconds
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    lateinit var firestore: FirebaseFirestore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            // Initialize Firebase first with persistence
            FirebaseApp.initializeApp(this)?.let { app ->
                // Enable offline persistence for Firestore
                Firebase.firestore.firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()

                firestore = Firebase.firestore

                // Enable disk persistence for Authentication
                FirebaseAuth.getInstance().apply {
                    firebaseAuthSettings.setAppVerificationDisabledForTesting(BuildConfig.DEBUG)
                }

                // Initialize App Check with proper error handling
                initializeAppCheck()

                // Set up Auth state listener with retry mechanism
                setupAuthStateListener()

                if (BuildConfig.DEBUG) {
                    app.setAutomaticResourceManagementEnabled(true)
                }
            } ?: throw IllegalStateException("Firebase initialization failed")

            // Initialize data sync after Firebase setup
            initializeDataSync()

            Log.d("PawsomePals", "Firebase initialization completed successfully")

        } catch (e: Exception) {
            Log.e("PawsomePals", "Firebase initialization failed", e)
            e.printStackTrace()
        }
    }

    private fun initializeAppCheck() {
        try {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.setTokenAutoRefreshEnabled(true)

            if (BuildConfig.DEBUG) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d("PawsomePals", "Initialized Firebase App Check in Debug mode")
            } else {
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d("PawsomePals", "Initialized Firebase App Check in Release mode")
            }
        } catch (e: Exception) {
            Log.e("PawsomePals", "App Check initialization failed", e)
            // Continue app initialization even if App Check fails
        }
    }

    private fun setupAuthStateListener() {
        var retryCount = 0

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            auth.currentUser?.let { user ->
                // Verify token validity
                user.getIdToken(false).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("PawsomePals", "User authenticated and token verified: ${user.uid}")
                        retryCount = 0 // Reset retry count on successful auth
                    } else if (retryCount < MAX_AUTH_RETRIES) {
                        retryCount++
                        // Retry authentication after delay
                        CoroutineScope(Dispatchers.IO).launch {
                            kotlinx.coroutines.delay(AUTH_RETRY_DELAY)
                            user.reload()
                        }
                    }
                }
            } ?: Log.d("PawsomePals", "User signed out")
        }
    }

    private fun initializeDataSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseAuth.getInstance().currentUser?.let { user ->
                    Log.d("PawsomePals", "Starting data sync for user: ${user.uid}")
                    dataManager.syncWithFirestore()
                } ?: Log.d("PawsomePals", "No authenticated user for initial sync")
            } catch (e: Exception) {
                Log.e("PawsomePals", "Data sync failed", e)
            }
        }
    }

    override fun onTerminate() {
        try {
            dataManager.cleanup()
        } catch (e: Exception) {
            Log.e("PawsomePals", "Cleanup failed", e)
        }
        super.onTerminate()
    }
}
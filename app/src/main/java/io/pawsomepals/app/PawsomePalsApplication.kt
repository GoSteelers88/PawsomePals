package io.pawsomepals.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class PawsomePalsApplication : Application(), Configuration.Provider {
    @Inject lateinit var dataManager: DataManager
    @Inject lateinit var remoteConfigManager: RemoteConfigManager
    @Inject lateinit var googleAuthManager: GoogleAuthManager
    @Inject lateinit var authStateManager: AuthStateManager  // Add this


    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this

        FirebaseAuth.getInstance().signOut()
        appScope.launch {
            authStateManager.clearAuthState()
        }


        // Initialize Firebase components in sequence
        initializeFirebaseComponents()
    }

    private fun initializeFirebaseComponents() {
        try {
            // 1. Initialize Firebase core
            FirebaseApp.initializeApp(this)?.let { app ->
                app.setAutomaticResourceManagementEnabled(true)

                // 2. Configure Firestore with limited persistence
                Firebase.firestore.apply {
                    firestoreSettings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(false)  // Disable persistence for fresh starts
                        .build()
                }

                // 3. Initialize AppCheck
                FirebaseAppCheck.getInstance().apply {
                    val provider = if (BuildConfig.DEBUG) {
                        DebugAppCheckProviderFactory.getInstance()
                    } else {
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    }
                    installAppCheckProviderFactory(provider)
                }

                // 4. Initialize RemoteConfig in background
                appScope.launch {
                    try {
                        remoteConfigManager.ensureInitialized()
                        val apiKey = remoteConfigManager.getMapsKey()
                        Log.d(TAG, "Initializing Places with key: ${apiKey.take(5)}...")
                        withContext(Dispatchers.Main) {
                            Places.initialize(applicationContext, apiKey)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Places init failed", e)
                    }
                }


                // 5. Setup auth state listener
                setupAuthStateListener()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun setupAuthStateListener() {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            auth.currentUser?.let { user ->
                appScope.launch {
                    handleAuthenticatedUser(user)
                }
            } ?: run {
                Log.d(TAG, "User signed out")
                appScope.launch {
                    dataManager.cleanup()
                }
            }
        }
    }

    private suspend fun handleAuthenticatedUser(user: FirebaseUser) {
        try {
            val tokenResult = withContext(Dispatchers.IO) {
                user.getIdToken(false).await()
            }

            if (tokenResult != null) {
                Log.d(TAG, "User authenticated: ${user.uid}")
                dataManager.syncWithFirestore()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth token error", e)
            retryAuthentication(user)
        }
    }

    private suspend fun retryAuthentication(user: FirebaseUser) {
        var retryCount = 0
        while (retryCount < MAX_AUTH_RETRIES) {
            delay(AUTH_RETRY_DELAY)
            try {
                user.reload().await()
                return
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed: ${retryCount + 1}/$MAX_AUTH_RETRIES")
                retryCount++
            }
        }
        // If all retries failed, sign out
        signOutUser()
    }

    private suspend fun signOutUser() {
        withContext(Dispatchers.IO) {
            try {
                dataManager.cleanup()
                Identity.getSignInClient(this@PawsomePalsApplication).signOut()
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.e(TAG, "Error signing out user", e)
            }
        }
    }

    override fun onTerminate() {
        appScope.launch {
            try {
                dataManager.cleanup()
            } finally {
                appScope.cancel()
                super.onTerminate()
            }
        }
    }

    companion object {
        private const val TAG = "PawsomePalsApp"
        private const val MAX_AUTH_RETRIES = 3
        private const val AUTH_RETRY_DELAY = 2000L

        lateinit var instance: PawsomePalsApplication
            private set
    }
}
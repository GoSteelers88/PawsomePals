package io.pawsomepals.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.service.location.PlacesInitializer
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class PawsomePalsApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var dataManager: DataManager

    @Inject
    lateinit var remoteConfigManager: RemoteConfigManager

    @Inject
    lateinit var googleAuthManager: GoogleAuthManager

    @Inject
    lateinit var placesInitializer: PlacesInitializer

    companion object {
        lateinit var instance: PawsomePalsApplication
            private set
        private const val MAX_AUTH_RETRIES = 3
        private const val AUTH_RETRY_DELAY = 2000L
        private const val MAX_INIT_RETRIES = 3
        private const val INIT_RETRY_DELAY = 1000L
        private const val TAG = "PawsomePalsApp"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    private lateinit var firestore: FirebaseFirestore
    private var initializationJob: Job? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeFirebaseWithRetry()
    }

    private fun initializeFirebaseWithRetry() {
        initializationJob = appScope.launch {
            var retryCount = 0
            var initialized = false

            while (!initialized && retryCount < MAX_INIT_RETRIES) {
                try {
                    FirebaseApp.initializeApp(this@PawsomePalsApplication)?.let { app ->
                        initializeFirebaseComponents(app)
                        setupSecurityFeatures()
                        setupAuthStateListener()
                        initializeDataSync()
                        initialized = true
                        Log.d(TAG, "Firebase initialization completed successfully")
                    } ?: throw IllegalStateException("Firebase initialization failed")
                } catch (e: Exception) {
                    retryCount++
                    Log.e(TAG, "Firebase initialization attempt $retryCount failed", e)
                    if (retryCount < MAX_INIT_RETRIES) {
                        delay(INIT_RETRY_DELAY * retryCount)
                    } else {
                        handleInitializationError(e)
                    }
                }
            }
        }
    }

    private fun initializeDataSync() {
        appScope.launch {
            try {
                FirebaseAuth.getInstance().currentUser?.let { user ->
                    Log.d(TAG, "Starting data sync for user: ${user.uid}")
                    dataManager.syncWithFirestore()
                } ?: Log.d(TAG, "No authenticated user for initial sync")
            } catch (e: Exception) {
                Log.e(TAG, "Data sync failed", e)
            }
        }
    }


    private fun initializeFirebaseComponents(app: FirebaseApp) {
        firestore = Firebase.firestore.apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
        }

        remoteConfigManager.initialize()
        remoteConfigManager.fetchAndActivate { success ->
            Log.d(TAG, "Remote Config fetch completed. Success: $success")
            if (success) {
                // Initialize Places API after Remote Config is ready
                initializePlacesApi()
            } else {
                // Retry Remote Config fetch with exponential backoff
                retryRemoteConfigFetch()
            }
        }

        if (BuildConfig.DEBUG) {
            app.setAutomaticResourceManagementEnabled(true)
            FirebaseAuth.getInstance().firebaseAuthSettings
                .setAppVerificationDisabledForTesting(true)
        }
    }
    private fun initializePlacesApi() {
        try {
            placesInitializer.initializePlaces()
            Log.d(TAG, "Places API initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Places API initialization failed", e)
            // Implement retry logic if needed
            retryPlacesInitialization()
        }
    }

    private fun retryPlacesInitialization() {
        appScope.launch {
            var retryCount = 0
            val maxRetries = 3

            while (retryCount < maxRetries) {
                delay(1000L * (1 shl retryCount)) // Exponential backoff
                try {
                    placesInitializer.initializePlaces()
                    Log.d(TAG, "Places API initialization retry successful")
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "Places API initialization retry failed", e)
                }
                retryCount++
            }
        }
    }

    private fun retryRemoteConfigFetch() {
        appScope.launch {
            var retryCount = 0
            val maxRetries = 3

            while (retryCount < maxRetries) {
                delay(1000L * (1 shl retryCount)) // Exponential backoff
                try {
                    remoteConfigManager.fetchAndActivate { success ->
                        if (success) {
                            Log.d(TAG, "Remote Config fetch retry successful")
                            return@fetchAndActivate
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Remote Config fetch retry failed", e)
                }
                retryCount++
            }
        }
    }

    private fun setupSecurityFeatures() {
        initializeAppCheck()
        setupGoogleAuthConfig()
    }

    private fun initializeAppCheck() {
        try {
            FirebaseAppCheck.getInstance().apply {
                setTokenAutoRefreshEnabled(true)

                val provider = if (BuildConfig.DEBUG) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }

                installAppCheckProviderFactory(provider)

                // Add App Check token listener
                addAppCheckListener { token ->
                    Log.d(TAG, "New App Check token received: ${token.token.take(10)}...")
                }

                Log.d(TAG, "App Check initialized in ${if (BuildConfig.DEBUG) "Debug" else "Release"} mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "App Check initialization failed", e)
            // Implement fallback or retry mechanism if needed
        }
    }


    private fun setupGoogleAuthConfig() {
        try {
            Identity.getSignInClient(this).apply {
                signOut().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Previous Google Sign-In state cleared")
                    } else {
                        Log.e(TAG, "Failed to clear Google Sign-In state", task.exception)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring Google Sign-In", e)
        }
    }

    private fun setupAuthStateListener() {
        val authStateManager = AuthStateManager()
        FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
            firebaseAuth.currentUser?.let { user ->
                appScope.launch {  // Add this coroutine scope
                    authStateManager.handleAuthenticatedUser(user)
                }
            } ?: run {
                Log.d(TAG, "User signed out")
                authStateManager.reset()
            }
        }
    }

    private inner class AuthStateManager {
        private var retryCount = 0
        private var authJob: Job? = null

        fun handleAuthenticatedUser(user: FirebaseUser) {
            authJob?.cancel()
            authJob = appScope.launch {
                try {
                    val tokenTask = user.getIdToken(false)
                    val tokenResult = withContext(Dispatchers.IO) {
                        tokenTask.await()
                    }

                    if (tokenResult != null) {
                        Log.d(TAG, "User authenticated: ${user.uid}")
                        retryCount = 0
                        initializeUserServices(user)
                    } else {
                        handleTokenError()
                    }
                } catch (e: Exception) {
                    handleTokenError()
                }
            }
        }
        private suspend fun handleTokenError() {
            if (retryCount < MAX_AUTH_RETRIES) {
                retryCount++
                withContext(Dispatchers.Main) {
                    retryAuthentication(FirebaseAuth.getInstance().currentUser!!)
                }
            } else {
                Log.e(TAG, "Max auth retries reached")
                signOutUser()
            }
        }


        private suspend fun initializeUserServices(user: FirebaseUser) {
            try {
                dataManager.syncWithFirestore()
                // Initialize other user-specific services here
                Log.d(TAG, "User services initialized for: ${user.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize user services", e)
                // Consider whether to retry or handle the error differently
                handleServiceInitializationError(e)
            }
        }

        private fun handleServiceInitializationError(error: Exception) {
            Log.e(TAG, "Service initialization error", error)
            FirebaseCrashlytics.getInstance().recordException(error)
            // Optionally retry or notify the user
        }

        private fun initializeDataSync() {
            appScope.launch {
                try {
                    FirebaseAuth.getInstance().currentUser?.let { user ->
                        Log.d(TAG, "Starting data sync for user: ${user.uid}")
                        dataManager.syncWithFirestore()
                    } ?: Log.d(TAG, "No authenticated user for initial sync")
                } catch (e: Exception) {
                    Log.e(TAG, "Data sync failed", e)
                }
            }
        }

        fun reset() {
            retryCount = 0
            authJob?.cancel()
        }
    }

    private suspend fun retryAuthentication(user: FirebaseUser) {
        delay(AUTH_RETRY_DELAY)
        try {
            user.reload().await()
            Log.d(TAG, "User reload successful")
        } catch (e: Exception) {
            Log.e(TAG, "User reload failed", e)
        }
    }

    private suspend fun signOutUser() {
        withContext(Dispatchers.IO) {
            try {
                googleAuthManager.signOut()
                FirebaseAuth.getInstance().signOut()
                Log.d(TAG, "User signed out due to auth failure")
            } catch (e: Exception) {
                Log.e(TAG, "Error signing out user", e)
            }
        }
    }

    private fun handleInitializationError(error: Exception) {
        Log.e(TAG, "Fatal initialization error", error)
        // Implement crash reporting
        FirebaseCrashlytics.getInstance().recordException(error)
    }

    override fun onTerminate() {
        runBlocking {
            try {
                initializationJob?.cancelAndJoin()
                dataManager.cleanup()
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed", e)
            }
        }
        super.onTerminate()
    }
}
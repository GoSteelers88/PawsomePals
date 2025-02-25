package io.pawsomepals.app.service.location

import android.content.Context
import android.util.Log
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigManager: RemoteConfigManager
) {
    private val initializationMutex = Mutex()
    private var isInitialized = false

    suspend fun ensureInitialized(): Boolean = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            if (isInitialized && Places.isInitialized()) {
                return@withContext true
            }

            try {
                if (Places.isInitialized()) {
                    Places.deinitialize()
                }

                val apiKey = remoteConfigManager.getMapsKey()
                Log.d(TAG, "Initializing Places with key length: ${apiKey.length}")

                Places.initialize(context.applicationContext, apiKey)

                // Verify initialization
                if (!Places.isInitialized()) {
                    throw IllegalStateException("Places failed to initialize")
                }

                isInitialized = true
                Log.d(TAG, "Places SDK initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Places initialization failed", e)
                isInitialized = false
                throw e
            }
        }
    }

    fun isPlacesInitialized(): Boolean {
        return isInitialized && Places.isInitialized()
    }

    suspend fun reinitializePlaces() = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            try {
                val apiKey = remoteConfigManager.getMapsKey()

                if (apiKey.isBlank() || !apiKey.startsWith("AIza")) {
                    throw IllegalStateException("Invalid API key format")
                }

                if (Places.isInitialized()) {
                    Places.deinitialize()
                }

                Places.initialize(context.applicationContext, apiKey)

                if (!Places.isInitialized()) {
                    throw IllegalStateException("Places reinitialization failed")
                }

                isInitialized = true
                Log.d(TAG, "Places API re-initialized successfully")
            } catch (e: Exception) {
                isInitialized = false
                Log.e(TAG, "Failed to re-initialize Places API", e)
                throw e
            }
        }
    }

    companion object {
        private const val TAG = "PlacesInitializer"
    }
}
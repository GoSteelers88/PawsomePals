package io.pawsomepals.app.service.location

import android.content.Context
import android.util.Log
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.utils.RemoteConfigManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigManager: RemoteConfigManager
) {
    companion object {
        private const val TAG = "PlacesInitializer"
    }

    fun initializePlaces() {
        try {
            if (!Places.isInitialized()) {
                val apiKey = remoteConfigManager.getMapsKey()
                if (apiKey.isNotBlank()) {
                    Places.initialize(context, apiKey)
                    Log.d(TAG, "Places API initialized successfully")
                } else {
                    throw IllegalStateException("Maps API key is blank")
                }
            } else {
                Log.d(TAG, "Places API already initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Places API", e)
            throw e // Rethrow to be handled by the application
        }
    }

    fun isPlacesInitialized(): Boolean {
        return Places.isInitialized()
    }

    fun reinitializePlaces() {
        try {
            // Force re-initialization with new key
            val apiKey = remoteConfigManager.getMapsKey()
            Places.initialize(context, apiKey)
            Log.d(TAG, "Places API re-initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-initialize Places API", e)
            throw e
        }
    }
}
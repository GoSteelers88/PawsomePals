package io.pawsomepals.app.di.modules

import android.content.Context
import android.util.Log
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.pawsomepals.app.utils.RemoteConfigManager
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object PlacesModule {
    @Provides
    @Singleton
    fun providePlacesClient(
        @ApplicationContext context: Context,
        remoteConfigManager: RemoteConfigManager
    ): PlacesClient {
        try {
            val apiKey = remoteConfigManager.getMapsKey()
            Log.d("PlacesModule", "Initializing Places with key length: ${apiKey.length}")

            if (!Places.isInitialized()) {
                Places.initialize(context.applicationContext, apiKey)
                Log.d("PlacesModule", "Places initialized: ${Places.isInitialized()}")
            }

            val client = Places.createClient(context)
            Log.d("PlacesModule", "PlacesClient created successfully")
            return client
        } catch (e: Exception) {
            Log.e("PlacesModule", "Error initializing Places: ${e.message}", e)
            throw e
        }
    }
}
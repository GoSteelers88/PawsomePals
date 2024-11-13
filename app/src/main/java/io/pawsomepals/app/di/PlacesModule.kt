package io.pawsomepals.app.di

import android.content.Context
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
        if (!Places.isInitialized()) {
            val apiKey = remoteConfigManager.getMapsKey()
            Places.initialize(context, apiKey)
        }
        return Places.createClient(context)
    }
}
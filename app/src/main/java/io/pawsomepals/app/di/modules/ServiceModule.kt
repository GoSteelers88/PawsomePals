package io.pawsomepals.app.di.modules

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.matching.MatchPreferences
import io.pawsomepals.app.service.matching.MatchingService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideMatchPreferences(): MatchPreferences {
        return MatchPreferences()
    }

    @Provides
    @Singleton
    fun provideMatchingService(
        locationService: LocationService,
        locationMatchingEngine: LocationMatchingEngine,
        matchPreferences: MatchPreferences
    ): MatchingService {
        return MatchingService(locationService, locationMatchingEngine, matchPreferences)
    }
}
package io.pawsomepals.app.service.matching

import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Use @Singleton instead of @Injectable
class MatchPreferences @Inject constructor() {
    val maxDistance: Double = 50.0
    val minCompatibilityScore: Double = 0.4
    val prioritizeEnergy: Boolean = false
    val prioritizeAge: Boolean = false
    val prioritizeBreed: Boolean = false
}

@Singleton // Add Singleton annotation
class MatchingService @Inject constructor(
    private val locationService: LocationService,
    private val locationMatchingEngine: LocationMatchingEngine,
    private val matchPreferences: MatchPreferences
) {
    // Need to add implementation from your existing MatchingService
    // All the match calculation methods should be moved here

    companion object {
        private const val MATCH_THRESHOLD = 0.7
        const val MAX_DISTANCE = 50.0
        private const val MIN_COMPATIBILITY_SCORE = 0.4
    }

    // Copy all your data classes and methods from the existing MatchingService
    data class MatchResult(
        val isMatch: Boolean,
        val compatibilityScore: Double,
        val reasons: List<MatchReason>,
        val distance: Double?,
        val warnings: List<String> = emptyList()
    )

    // Add all other methods from your existing MatchingService
}
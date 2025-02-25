package io.pawsomepals.app.discovery

import android.util.Log
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.discovery.queue.LocationAwareQueueManager
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ProfileDiscoveryService @Inject constructor(
    private val dogProfileRepository: DogProfileRepository,
    private val locationService: LocationService,
    private val locationMatchingEngine: LocationMatchingEngine,
    private val matchingService: MatchingService,
    private val queueManager: LocationAwareQueueManager
) {
    companion object {
        private const val TAG = "ProfileDiscoveryService"
        private const val DEFAULT_RADIUS_KM = 50.0
        private const val NEW_PROFILE_BOOST_DAYS = 7
        private const val MAX_BATCH_SIZE = 20
    }

    data class ProfileScore(
        val dog: Dog,
        val baseScore: Double,
        val locationScore: Double,
        val activityScore: Double,
        val newUserBoost: Double,
        val finalScore: Double
    )

    data class DiscoveryPreferences(
        val maxDistance: Double = DEFAULT_RADIUS_KM,
        val prioritizeLocation: Boolean = true,
        val prioritizeActivity: Boolean = true,
        val includeNewProfiles: Boolean = true,
        val excludedUserIds: Set<String> = emptySet(),    // Add this
        val excludedDogIds: Set<String> = emptySet()      // Add this
    )
    suspend fun discoverProfiles(
        currentDog: Dog,
        preferences: DiscoveryPreferences = DiscoveryPreferences()
    ): Flow<List<Dog>> = flow {
        try {
            Log.d(TAG, "Starting profile discovery for dog: ${currentDog.id}")

            // Create exclusion sets
            val excludedUserIds = preferences.excludedUserIds + currentDog.ownerId
            val excludedDogIds = preferences.excludedDogIds + currentDog.id

            // Get profiles with enhanced filtering
            val allDogs = dogProfileRepository.getSwipingProfiles(100)
                .getOrDefault(emptyList())
                .filter { dog ->
                    !excludedUserIds.contains(dog.ownerId) &&  // Filter out excluded users
                            !excludedDogIds.contains(dog.id) &&        // Filter out excluded dogs
                            dog.id != currentDog.id &&                 // Double check current dog
                            dog.ownerId != currentDog.ownerId          // Double check owner
                }

            Log.d(TAG, """
            Filtering Results:
            - Total dogs before filtering: ${allDogs.size}
            - Excluded user IDs: ${excludedUserIds.size}
            - Excluded dog IDs: ${excludedDogIds.size}
            - Dogs after filtering: ${allDogs.filter { it.id != currentDog.id }.size}
        """.trimIndent())

            // Get current location
            val currentLocation = locationService.getLastKnownLocation()
            Log.d(TAG, "Current location: $currentLocation")

            // Get nearby profiles
            val nearbyDogs = if (currentLocation != null && currentDog.latitude != null && currentDog.longitude != null) {
                dogProfileRepository.getDogsByLocation(
                    latitude = currentDog.latitude!!,
                    longitude = currentDog.longitude!!,
                    radius = preferences.maxDistance
                ).getOrDefault(emptyList())
                    .filter { dog ->
                        !excludedUserIds.contains(dog.ownerId) &&
                                !excludedDogIds.contains(dog.id)
                    }
            } else {
                allDogs
            }

            // Score and rank profiles (keep your existing logic)
            val scoredProfiles = nearbyDogs
                .map { dog ->
                    val score = scoreProfile(
                        currentDog = currentDog,
                        candidateDog = dog,
                        preferences = preferences
                    )
                    score
                }
                .sortedByDescending { it.finalScore }

            Log.d(TAG, "Final scored profiles count: ${scoredProfiles.size}")

            // Add to queue manager
            currentLocation?.let { location ->
                queueManager.addBatchToQueue(
                    scoredProfiles.map { it.dog },
                    location.latitude,
                    location.longitude
                )
                Log.d(TAG, "Added ${scoredProfiles.size} profiles to queue")
            }

            emit(scoredProfiles.map { it.dog })

        } catch (e: Exception) {
            Log.e(TAG, "Error discovering profiles", e)
            emit(emptyList())
        }
    }

    private fun validateProfile(
        profile: Dog,
        currentDog: Dog,
        excludedUserIds: Set<String>,
        excludedDogIds: Set<String>
    ): Boolean {
        return profile.id != currentDog.id &&
                profile.ownerId != currentDog.ownerId &&
                !excludedUserIds.contains(profile.ownerId) &&
                !excludedDogIds.contains(profile.id) &&
                profile.id.isNotBlank() &&
                profile.ownerId.isNotBlank()
    }

    private suspend fun scoreProfile(
        currentDog: Dog,
        candidateDog: Dog,
        preferences: DiscoveryPreferences
    ): ProfileScore {
        // Calculate base compatibility score
        val matchResult = matchingService.calculateMatch(currentDog, candidateDog)
        val baseScore = matchResult.compatibilityScore

        // Calculate location score
        val locationScore = calculateLocationScore(
            currentDog = currentDog,
            candidateDog = candidateDog,
            maxDistance = preferences.maxDistance
        )

        // Calculate activity score
        val activityScore = calculateActivityScore(candidateDog)

        // Calculate new user boost
        val newUserBoost = if (preferences.includeNewProfiles) {
            calculateNewUserBoost(candidateDog)
        } else {
            0.0
        }

        // Calculate final weighted score
        val finalScore = calculateFinalScore(
            baseScore = baseScore,
            locationScore = locationScore,
            activityScore = activityScore,
            newUserBoost = newUserBoost,
            preferences = preferences
        )

        return ProfileScore(
            dog = candidateDog,
            baseScore = baseScore,
            locationScore = locationScore,
            activityScore = activityScore,
            newUserBoost = newUserBoost,
            finalScore = finalScore
        )
    }

    private fun calculateLocationScore(
        currentDog: Dog,
        candidateDog: Dog,
        maxDistance: Double
    ): Double {
        return locationMatchingEngine.calculateLocationScore(
            currentDog,
            candidateDog
        ).score.coerceIn(0.0, 1.0)
    }

    private fun calculateActivityScore(dog: Dog): Double {
        // Just use profile completeness for now
        return calculateProfileCompleteness(dog)
    }

    private fun calculateProfileCompleteness(dog: Dog): Double {
        var completeness = 0.0
        var totalFields = 0.0

        // Only check essential fields
        if (!dog.name.isNullOrBlank()) completeness++
        if (!dog.breed.isNullOrBlank()) completeness++
        if (dog.age != null) completeness++
        totalFields += 3

        return (completeness / totalFields).coerceIn(0.3, 1.0) // Minimum score of 0.3
    }
    private fun calculateRecentActivity(dog: Dog): Double {
        // Simple activity score based on last active timestamp
        val now = System.currentTimeMillis()
        val daysSinceActive = (now - (dog.lastActive ?: now)) / (1000 * 60 * 60 * 24.0)
        return max(0.0, 1.0 - (daysSinceActive / 30.0)) // Scale over 30 days
    }


    private fun calculateNewUserBoost(dog: Dog): Double {
        val now = System.currentTimeMillis()
        val daysOnPlatform = (now - (dog.created ?: now)) / (1000 * 60 * 60 * 24.0)
        return if (daysOnPlatform <= NEW_PROFILE_BOOST_DAYS) {
            max(0.0, 1.0 - (daysOnPlatform / NEW_PROFILE_BOOST_DAYS))
        } else {
            0.0
        }
    }

    private fun calculateFinalScore(
        baseScore: Double,
        locationScore: Double,
        activityScore: Double,
        newUserBoost: Double,
        preferences: DiscoveryPreferences
    ): Double {
        // Base weights
        var locationWeight = if (preferences.prioritizeLocation) 0.4 else 0.3
        var activityWeight = if (preferences.prioritizeActivity) 0.2 else 0.1
        val baseWeight = 0.4
        val boostWeight = 0.1

        // Normalize weights
        val totalWeight = locationWeight + activityWeight + baseWeight + boostWeight
        locationWeight /= totalWeight
        activityWeight /= totalWeight
        val normalizedBaseWeight = baseWeight / totalWeight
        val normalizedBoostWeight = boostWeight / totalWeight

        return (baseScore * normalizedBaseWeight) +
                (locationScore * locationWeight) +
                (activityScore * activityWeight) +
                (newUserBoost * normalizedBoostWeight)
    }

    suspend fun refreshDiscoveryQueue(currentDog: Dog) {
        try {
            queueManager.clearQueue()
            discoverProfiles(currentDog).collect { profiles ->
                Log.d(TAG, "Refreshed discovery queue with ${profiles.size} profiles")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing discovery queue", e)
        }
    }

    fun getQueueStats() = queueManager.getQueueStats()
}
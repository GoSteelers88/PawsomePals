
package io.pawsomepals.app.service

import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.data.model.MatchReason.AGE_COMPATIBILITY
import io.pawsomepals.app.data.model.MatchReason.BREED_COMPATIBILITY
import io.pawsomepals.app.data.model.MatchReason.ENERGY_LEVEL_MATCH
import io.pawsomepals.app.data.model.MatchReason.HEALTH_COMPATIBILITY
import io.pawsomepals.app.data.model.MatchReason.LOCATION_PROXIMITY
import io.pawsomepals.app.data.model.MatchReason.PLAY_STYLE_MATCH
import io.pawsomepals.app.data.model.MatchReason.SIZE_COMPATIBILITY
import io.pawsomepals.app.data.model.MatchReason.TEMPERAMENT_MATCH
import io.pawsomepals.app.data.model.MatchReason.TRAINING_LEVEL_MATCH
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.matching.MatchPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.abs


class MatchingService @Inject constructor( // Add @Inject
    private val locationService: LocationService,
    private val locationMatchingEngine: LocationMatchingEngine, // Add this

    private val matchPreferences: MatchPreferences
) {


    data class MatchScore(
        val baseCompatibility: Double,
        val locationScore: Double,
        val distanceKm: Double?,
        val commonAreas: List<DogFriendlyLocation>,
        val matchReasons: List<MatchReason>,
        val warnings: List<String>
    )

    data class MatchResult(
        val isMatch: Boolean,
        val compatibilityScore: Double,
        val reasons: List<MatchReason>,
        val distance: Double?,
        val warnings: List<String> = emptyList()
    )



    private data class CompatibilityDetails(
        val score: Double,
        val reasons: List<MatchReason>
    )

    private data class ScoringFactor(
        val type: String,
        val score: Double,
        val weight: Double
    )

    suspend fun calculateDetailedMatch(dog1: Dog, dog2: Dog): MatchScore {
        // Get location-based score first
        val locationScore = locationMatchingEngine.calculateLocationScore(dog1, dog2)

        // Get basic compatibility
        val compatibilityDetails = getDetailedCompatibility(dog1, dog2)

        // Calculate warnings
        val warnings = mutableListOf<String>()
        if (dog1.isSpayedNeutered != dog2.isSpayedNeutered) {
            warnings.add("Different spay/neuter status")
        }

        // Get common areas
        val commonAreas = locationMatchingEngine.findCommonAreas(dog1, dog2)

        return MatchScore(
            baseCompatibility = compatibilityDetails.score,
            locationScore = locationScore.score,
            distanceKm = calculateDistance(dog1, dog2),
            commonAreas = commonAreas,
            matchReasons = compatibilityDetails.reasons,
            warnings = warnings
        )
    }

    suspend fun calculateMatch(dog1: Dog, dog2: Dog): MatchResult {
        val detailedMatch = calculateDetailedMatch(dog1, dog2)

        // Combine scores with weighted importance
        val combinedScore = (detailedMatch.baseCompatibility * 0.6) +
                (detailedMatch.locationScore * 0.4)

        return MatchResult(
            isMatch = combinedScore >= MATCH_THRESHOLD,
            compatibilityScore = combinedScore,
            reasons = detailedMatch.matchReasons,
            distance = detailedMatch.distanceKm,
            warnings = detailedMatch.warnings
        )
    }
    suspend fun findNearbyMatches(
        dog: Dog,
        radius: Double,
        limit: Int = 20
    ): Flow<List<Dog>> = flow {
        try {
            val nearbyDogs = locationMatchingEngine.findDogsInRadius(
                latitude = dog.latitude ?: return@flow,
                longitude = dog.longitude ?: return@flow,
                radius = radius
            )

            // Score and sort nearby dogs
            val scoredDogs = nearbyDogs.mapNotNull { nearbyDog ->
                try {
                    val score = calculateDetailedMatch(dog, nearbyDog)
                    nearbyDog to score
                } catch (e: Exception) {
                    null
                }
            }.sortedWith(
                compareByDescending<Pair<Dog, MatchScore>> { it.second.locationScore }
                    .thenByDescending { it.second.baseCompatibility }
            )

            emit(scoredDogs.take(limit).map { it.first })
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
    suspend fun calculateBatchMatches(
        targetDog: Dog,
        candidates: List<Dog>
    ): List<Pair<Dog, MatchScore>> {
        return candidates.map { candidate ->
            candidate to calculateDetailedMatch(targetDog, candidate)
        }.sortedByDescending { it.second.baseCompatibility }
    }

    private fun getDetailedCompatibility(profile1: Dog, profile2: Dog): CompatibilityDetails {
        val scoreFactors = mutableListOf<ScoringFactor>()
        val matchReasons = mutableListOf<MatchReason>()

        // Check breed compatibility
        if (profile1.breed == profile2.breed) {
            addScoringFactor(
                factors = scoreFactors,
                reasons = matchReasons,
                type = "breed",
                score = 1.0,
                weight = if (matchPreferences.prioritizeBreed) 0.25 else 0.15,
                reason = BREED_COMPATIBILITY
            )
        }

        // Energy level cmpatibility
        val energyScore = getEnergyLevelCompatibility(profile1.energyLevel, profile2.energyLevel)
        if (energyScore > 0.7) {
            addScoringFactor(
                factors = scoreFactors,
                reasons = matchReasons,
                type = "energy",
                score = energyScore,
                weight = if (matchPreferences.prioritizeEnergy) 0.25 else 0.2,
                reason = ENERGY_LEVEL_MATCH
            )
        }

        // Age compatibility
        val ageScore = getAgeCompatibility(profile1.age ?: 0, profile2.age ?: 0)
        if (ageScore > 0.7) {
            addScoringFactor(
                factors = scoreFactors,
                reasons = matchReasons,
                type = "age",
                score = ageScore,
                weight = if (matchPreferences.prioritizeAge) 0.2 else 0.15,
                reason = AGE_COMPATIBILITY
            )
        }

        // Size compatibility
        val sizeScore = getSizeCompatibility(profile1.size, profile2.size)
        if (sizeScore > 0.7) {
            addScoringFactor(
                factors = scoreFactors,
                reasons = matchReasons,
                type = "size",
                score = sizeScore,
                weight = 0.15,
                reason = SIZE_COMPATIBILITY
            )
        }

        // Temperament compatibility
        if (!profile1.friendliness.isNullOrEmpty() && !profile2.friendliness.isNullOrEmpty()) {
            val temperamentScore = getFriendlinessCompatibility(profile1.friendliness, profile2.friendliness)
            if (temperamentScore > 0.7) {
                addScoringFactor(
                    factors = scoreFactors,
                    reasons = matchReasons,
                    type = "temperament",
                    score = temperamentScore,
                    weight = 0.15,
                    reason = TEMPERAMENT_MATCH
                )
            }
        }

        // Location compatibility
        val locationScore = getLocationCompatibility(profile1, profile2)
        if (locationScore > 0) {
            addScoringFactor(
                factors = scoreFactors,
                reasons = matchReasons,
                type = "location",
                score = locationScore,
                weight = 0.2,
                reason = LOCATION_PROXIMITY
            )
        }

        // Exercise compatibility
        if (!profile1.exerciseNeeds.isNullOrEmpty() && !profile2.exerciseNeeds.isNullOrEmpty()) {
            val exerciseScore = getExerciseCompatibility(profile1.exerciseNeeds!!, profile2.exerciseNeeds!!)
            if (exerciseScore > 0.7) {
                addScoringFactor(
                    factors = scoreFactors,
                    reasons = matchReasons,
                    type = "exercise",
                    score = exerciseScore,
                    weight = 0.1,
                    reason = PLAY_STYLE_MATCH
                )
            }
        }

        // Training compatibility
        if (!profile1.trainability.isNullOrEmpty() && !profile2.trainability.isNullOrEmpty()) {
            val trainingScore = getTrainingCompatibility(profile1.trainability!!, profile2.trainability!!)
            if (trainingScore > 0.7) {
                addScoringFactor(
                    factors = scoreFactors,
                    reasons = matchReasons,
                    type = "training",
                    score = trainingScore,
                    weight = 0.1,
                    reason = TRAINING_LEVEL_MATCH
                )
            }
        }

        // Special needs compatibility
        if (!profile1.specialNeeds.isNullOrEmpty() || !profile2.specialNeeds.isNullOrEmpty()) {
            val specialNeedsScore = getSpecialNeedsCompatibility(profile1.specialNeeds, profile2.specialNeeds)
            if (specialNeedsScore > 0.7) {
                addScoringFactor(
                    factors = scoreFactors,
                    reasons = matchReasons,
                    type = "health",
                    score = specialNeedsScore,
                    weight = 0.1,
                    reason = HEALTH_COMPATIBILITY
                )
            }
        }

        // Calculate final score
        val totalWeight = scoreFactors.sumByDouble { it.weight }
        val weightedScore = scoreFactors.sumByDouble { it.score * it.weight }
        val finalScore = if (totalWeight > 0) weightedScore / totalWeight else 0.0

        return CompatibilityDetails(finalScore, matchReasons)
    }

    private fun addScoringFactor(
        factors: MutableList<ScoringFactor>,
        reasons: MutableList<MatchReason>,
        type: String,
        score: Double,
        weight: Double,
        reason: MatchReason
    ) {
        factors.add(ScoringFactor(type, score, weight))
        if (score >= 0.7) {
            reasons.add(reason)
        }
    }

    private fun getEnergyLevelCompatibility(energy1: String?, energy2: String?): Double {
        if (energy1.isNullOrEmpty() || energy2.isNullOrEmpty()) return 0.5
        val level1 = energyLevelToNumber(energy1)
        val level2 = energyLevelToNumber(energy2)
        return 1.0 - (abs(level1 - level2).toDouble() / 3.0)
    }

    private fun energyLevelToNumber(level: String): Int = when (level.toLowerCase()) {
        "low" -> 1
        "moderate" -> 2
        "high" -> 3
        else -> 2
    }

    private fun getAgeCompatibility(age1: Int, age2: Int): Double {
        val ageDiff = abs(age1 - age2)
        return when {
            ageDiff <= 2 -> 1.0
            ageDiff <= 4 -> 0.7
            ageDiff <= 6 -> 0.4
            else -> 0.2
        }
    }

    private fun getFriendlinessCompatibility(friendliness1: String?, friendliness2: String?): Double {
        if (friendliness1.isNullOrEmpty() || friendliness2.isNullOrEmpty()) return 0.5
        val level1 = friendlinessToNumber(friendliness1)
        val level2 = friendlinessToNumber(friendliness2)
        return 1.0 - (abs(level1 - level2).toDouble() / 3.0)
    }

    private fun friendlinessToNumber(level: String): Int = when (level.toLowerCase()) {
        "shy" -> 1
        "selective" -> 2
        "friendly" -> 3
        else -> 2
    }

    private fun getLocationCompatibility(profile1: Dog, profile2: Dog): Double {
        return locationMatchingEngine.calculateLocationScore(profile1, profile2).score
    }

    companion object {
        private const val MATCH_THRESHOLD = 0.7
        const val MAX_DISTANCE = 50.0 // km
        private const val MIN_COMPATIBILITY_SCORE = 0.4

        // Add new constants for location weights
        const val LOCATION_WEIGHT = 0.4
        const val COMPATIBILITY_WEIGHT = 0.6
    }

    private fun getSizeCompatibility(size1: String?, size2: String?): Double {
        if (size1.isNullOrEmpty() || size2.isNullOrEmpty()) return 0.5
        return when {
            size1 == size2 -> 1.0
            abs(sizeToNumber(size1) - sizeToNumber(size2)) == 1 -> 0.5
            else -> 0.0
        }
    }

    private fun sizeToNumber(size: String): Int = when (size.toLowerCase()) {
        "small" -> 1
        "medium" -> 2
        "large" -> 3
        "extra large" -> 4
        else -> 2
    }

    private fun getExerciseCompatibility(exercise1: String, exercise2: String): Double {
        val level1 = exerciseLevelToNumber(exercise1)
        val level2 = exerciseLevelToNumber(exercise2)
        return 1.0 - (abs(level1 - level2).toDouble() / 4.0)
    }

    private fun exerciseLevelToNumber(level: String): Int = when (level.toLowerCase()) {
        "minimal" -> 1
        "moderate" -> 2
        "high" -> 3
        "very high" -> 4
        else -> 2
    }

    private fun getTrainingCompatibility(training1: String, training2: String): Double {
        val level1 = trainingLevelToNumber(training1)
        val level2 = trainingLevelToNumber(training2)
        return 1.0 - (abs(level1 - level2).toDouble() / 3.0)
    }

    private fun trainingLevelToNumber(level: String): Int = when (level.toLowerCase()) {
        "basic" -> 1
        "intermediate" -> 2
        "advanced" -> 3
        else -> 1
    }

    private fun getSpecialNeedsCompatibility(needs1: String?, needs2: String?): Double {
        return when {
            needs1.isNullOrEmpty() && needs2.isNullOrEmpty() -> 1.0
            needs1.isNullOrEmpty() || needs2.isNullOrEmpty() -> 0.7
            else -> 0.5 // Both have special needs - might need more careful consideration
        }
    }

    private fun calculateDistance(profile1: Dog, profile2: Dog): Double? {
        val lat1 = profile1.latitude
        val lon1 = profile1.longitude
        val lat2 = profile2.latitude
        val lon2 = profile2.longitude

        return if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
            locationService.calculateDistance(lat1, lon1, lat2, lon2).toDouble()
        } else null
    }

    suspend fun isMatch(dog1: Dog, dog2: Dog): Boolean {
        return calculateMatch(dog1, dog2).isMatch
    }

    suspend fun getCompatibilityScore(dog1: Dog, dog2: Dog): Double {
        return calculateMatch(dog1, dog2).compatibilityScore
    }
}

package com.example.pawsomepals.service

import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.MatchReason
import kotlin.math.abs

class MatchingService(
    private val locationService: LocationService,
    private val matchPreferences: MatchPreferences
) {
    companion object {
        private const val MATCH_THRESHOLD = 0.7
        const val MAX_DISTANCE = 50.0 // km
        private const val MIN_COMPATIBILITY_SCORE = 0.4
    }

    data class MatchResult(
        val isMatch: Boolean,
        val compatibilityScore: Double,
        val reasons: List<MatchReason>,
        val distance: Double?,
        val warnings: List<String> = emptyList()
    )

    data class MatchPreferences(
        val maxDistance: Double = MAX_DISTANCE,
        val minCompatibilityScore: Double = MIN_COMPATIBILITY_SCORE,
        val prioritizeEnergy: Boolean = false,
        val prioritizeAge: Boolean = false,
        val prioritizeBreed: Boolean = false
    )

    fun calculateMatch(profile1: Dog, profile2: Dog): MatchResult {
        val compatibilityDetails = getDetailedCompatibility(profile1, profile2)
        val distance = calculateDistance(profile1, profile2)

        val warnings = mutableListOf<String>()
        if (profile1.isSpayedNeutered != profile2.isSpayedNeutered) {
            warnings.add("Different spay/neuter status")
        }

        return MatchResult(
            isMatch = compatibilityDetails.score >= MATCH_THRESHOLD,
            compatibilityScore = compatibilityDetails.score,
            reasons = compatibilityDetails.reasons,
            distance = distance,
            warnings = warnings
        )
    }

    private data class CompatibilityDetails(
        val score: Double,
        val reasons: List<MatchReason>
    )
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

    private fun sizeToNumber(size: String): Int = when (size.toLowerCase()) {
        "small" -> 1
        "medium" -> 2
        "large" -> 3
        "extra large" -> 4
        else -> 2
    }

    private fun getLocationCompatibility(profile1: Dog, profile2: Dog): Double {
        val distance = calculateDistance(profile1, profile2)
        return when {
            distance == null -> 0.0
            distance <= matchPreferences.maxDistance * 0.3 -> 1.0
            distance <= matchPreferences.maxDistance * 0.6 -> 0.7
            distance <= matchPreferences.maxDistance -> 0.4
            else -> 0.0
        }
    }


    private fun getDetailedCompatibility(profile1: Dog, profile2: Dog): CompatibilityDetails {
        val scoreFactors = mutableListOf<ScoringFactor>()
        val reasons = mutableListOf<MatchReason>()

        // Core compatibility factors
        addScoringFactor(scoreFactors, reasons, "size",
            getSizeCompatibility(profile1.size, profile2.size),
            0.15,
            "Similar size"
        )

        addScoringFactor(scoreFactors, reasons, "energy",
            getEnergyLevelCompatibility(profile1.energyLevel, profile2.energyLevel),
            if (matchPreferences.prioritizeEnergy) 0.25 else 0.2,
            "Matching energy levels"
        )

        addScoringFactor(scoreFactors, reasons, "age",
            getAgeCompatibility(profile1.age ?: 0, profile2.age ?: 0),
            if (matchPreferences.prioritizeAge) 0.2 else 0.15,
            "Compatible age group"
        )

        // Behavioral compatibility
        addScoringFactor(scoreFactors, reasons, "friendliness",
            getFriendlinessCompatibility(profile1.friendliness, profile2.friendliness),
            0.15,
            "Similar friendliness levels"
        )

        // Exercise and training compatibility
        if (!profile1.exerciseNeeds.isNullOrEmpty() && !profile2.exerciseNeeds.isNullOrEmpty()) {
            addScoringFactor(scoreFactors, reasons, "exercise",
                getExerciseCompatibility(profile1.exerciseNeeds!!, profile2.exerciseNeeds!!),
                0.1,
                "Matching exercise needs"
            )
        }

        // Location compatibility
        val locationScore = getLocationCompatibility(profile1, profile2)
        if (locationScore > 0) {
            addScoringFactor(scoreFactors, reasons, "location",
                locationScore,
                0.2,
                "Within preferred distance"
            )
        }

        // Special considerations
        addSpecialConsiderations(profile1, profile2, scoreFactors, reasons)

        // Calculate final score
        val totalWeight = scoreFactors.sumByDouble { it.weight }
        val weightedScore = scoreFactors.sumByDouble { it.score * it.weight }
        val finalScore = if (totalWeight > 0) weightedScore / totalWeight else 0.0

        return CompatibilityDetails(finalScore, reasons)
    }

    private data class ScoringFactor(
        val type: String,
        val score: Double,
        val weight: Double
    )

    private fun addScoringFactor(
        factors: MutableList<ScoringFactor>,
        reasons: MutableList<MatchReason>,
        type: String,
        score: Double,
        weight: Double,
        reasonText: String
    ) {
        factors.add(ScoringFactor(type, score, weight))
        if (score >= 0.7) { // Only add as a reason if it's a strong match
            reasons.add(MatchReason(type, reasonText, score))
        }
    }

    private fun addSpecialConsiderations(
        profile1: Dog,
        profile2: Dog,
        factors: MutableList<ScoringFactor>,
        reasons: MutableList<MatchReason>
    ) {
        // Handle special needs compatibility
        if (!profile1.specialNeeds.isNullOrEmpty() || !profile2.specialNeeds.isNullOrEmpty()) {
            val specialNeedsScore = getSpecialNeedsCompatibility(
                profile1.specialNeeds,
                profile2.specialNeeds
            )
            addScoringFactor(factors, reasons, "specialNeeds",
                specialNeedsScore,
                0.1,
                "Compatible special needs consideration"
            )
        }

        // Training level compatibility
        if (!profile1.trainability.isNullOrEmpty() && !profile2.trainability.isNullOrEmpty()) {
            val trainingScore = getTrainingCompatibility(
                profile1.trainability!!,
                profile2.trainability!!
            )
            addScoringFactor(factors, reasons, "training",
                trainingScore,
                0.1,
                "Similar training levels"
            )
        }
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

    private fun getSpecialNeedsCompatibility(needs1: String?, needs2: String?): Double {
        if (needs1.isNullOrEmpty() && needs2.isNullOrEmpty()) return 1.0
        if (needs1.isNullOrEmpty() || needs2.isNullOrEmpty()) return 0.7
        return 0.5 // Both have special needs - might need more careful consideration
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

    // Previous compatibility functions remain the same but are now private
    private fun getSizeCompatibility(size1: String?, size2: String?): Double {
        if (size1 == null || size2 == null) return 0.5
        return when {
            size1 == size2 -> 1.0
            abs(sizeToNumber(size1) - sizeToNumber(size2)) == 1 -> 0.5
            else -> 0.0
        }
    }

    fun isMatch(dog1: Dog, dog2: Dog): Boolean {
        val result = calculateMatch(dog1, dog2)
        return result.isMatch
    }

    fun getCompatibilityScore(dog1: Dog, dog2: Dog): Double {
        val result = calculateMatch(dog1, dog2)
        return result.compatibilityScore
    }

    private fun calculateDistance(profile1: Dog, profile2: Dog): Double? {
        val lat1 = profile1.latitude
        val lon1 = profile1.longitude
        val lat2 = profile2.latitude
        val lon2 = profile2.longitude

        return if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
            locationService.calculateDistance(lat1, lon1, lat2, lon2)?.toDouble()
        } else null
    }
}

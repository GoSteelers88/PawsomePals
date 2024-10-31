package com.example.pawsomepals.service

import com.example.pawsomepals.data.model.Dog
import kotlin.math.abs

class MatchingService(private val locationService: LocationService) {
    // Threshold for considering a match
    private val MATCH_THRESHOLD = 0.7
    private val MAX_DISTANCE = 50.0 // Maximum distance in km for consideration

    fun isMatch(profile1: Dog, profile2: Dog): Boolean {
        return getCompatibilityScore(profile1, profile2) >= MATCH_THRESHOLD
    }

    fun getCompatibilityScore(profile1: Dog, profile2: Dog): Double {
        var score = 0.0
        var totalWeight = 0.0

        // Size compatibility (weight: 0.15)
        score += getSizeCompatibility(profile1.size, profile2.size) * 0.15
        totalWeight += 0.15

        // Energy level compatibility (weight: 0.2)
        score += getEnergyLevelCompatibility(profile1.energyLevel, profile2.energyLevel) * 0.2
        totalWeight += 0.2

        // Age compatibility (weight: 0.15)
        score += getAgeCompatibility(profile1.age ?: 0, profile2.age ?: 0) * 0.15
        totalWeight += 0.15

        // Breed compatibility (weight: 0.1)
        score += getBreedCompatibility(profile1.breed, profile2.breed) * 0.1
        totalWeight += 0.1

        // Friendliness compatibility (weight: 0.15)
        score += getFriendlinessCompatibility(profile1.friendliness, profile2.friendliness) * 0.15
        totalWeight += 0.15

        // Location compatibility (weight: 0.2)
        score += getLocationCompatibility(profile1, profile2) * 0.2
        totalWeight += 0.2

        // Additional factors (if available)
        if (profile1.isSpayedNeutered != null && profile2.isSpayedNeutered != null) {
            score += if (profile1.isSpayedNeutered == profile2.isSpayedNeutered) 0.05 else 0.0
            totalWeight += 0.05
        }

        // Normalize the score
        return if (totalWeight > 0) score / totalWeight else 0.0
    }

    private fun getSizeCompatibility(size1: String?, size2: String?): Double {
        if (size1 == null || size2 == null) return 0.5
        return when {
            size1 == size2 -> 1.0
            abs(sizeToNumber(size1) - sizeToNumber(size2)) == 1 -> 0.5
            else -> 0.0
        }
    }

    private fun sizeToNumber(size: String): Int {
        return when (size.toLowerCase()) {
            "small" -> 1
            "medium" -> 2
            "large" -> 3
            else -> 0
        }
    }

    private fun getEnergyLevelCompatibility(energy1: String?, energy2: String?): Double {
        if (energy1 == null || energy2 == null) return 0.5
        val diff = abs(energyLevelToNumber(energy1) - energyLevelToNumber(energy2))
        return 1.0 - (diff.toDouble() / 4.0)
    }

    private fun energyLevelToNumber(energyLevel: String): Int {
        return when (energyLevel.toLowerCase()) {
            "low" -> 1
            "medium" -> 2
            "high" -> 3
            "very high" -> 4
            else -> 0
        }
    }

    private fun getAgeCompatibility(age1: Int, age2: Int): Double {
        val ageDiff = abs(age1 - age2)
        return when {
            ageDiff <= 2 -> 1.0
            ageDiff <= 4 -> 0.7
            ageDiff <= 6 -> 0.4
            else -> 0.1
        }
    }

    private fun getBreedCompatibility(breed1: String?, breed2: String?): Double {
        if (breed1 == null || breed2 == null) return 0.5
        return if (breed1 == breed2) 1.0 else 0.5
    }

    private fun getFriendlinessCompatibility(friendliness1: String?, friendliness2: String?): Double {
        if (friendliness1 == null || friendliness2 == null) return 0.5
        return when {
            friendliness1 == friendliness2 -> 1.0
            abs(friendlinessToNumber(friendliness1) - friendlinessToNumber(friendliness2)) == 1 -> 0.7
            else -> 0.4
        }
    }

    private fun friendlinessToNumber(friendliness: String): Int {
        return when (friendliness.toLowerCase()) {
            "shy" -> 1
            "friendly" -> 2
            "very friendly" -> 3
            else -> 0
        }
    }

    private fun getLocationCompatibility(profile1: Dog, profile2: Dog): Double {
        val lat1 = profile1.latitude
        val lon1 = profile1.longitude
        val lat2 = profile2.latitude
        val lon2 = profile2.longitude

        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0.5 // Default score if location data is missing
        }

        val distance = locationService.calculateDistance(lat1, lon1, lat2, lon2)
        return if (distance <= MAX_DISTANCE) {
            1 - (distance / MAX_DISTANCE)
        } else {
            0.0
        }
    }
}
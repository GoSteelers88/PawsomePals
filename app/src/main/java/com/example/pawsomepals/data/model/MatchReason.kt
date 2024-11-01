package com.example.pawsomepals.data.model

import com.google.gson.annotations.SerializedName

data class MatchReason(
    @SerializedName("type")
    val type: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("strength")
    val strength: Double, // 0.0 to 1.0

    @SerializedName("details")
    val details: Map<String, String> = emptyMap()
) {
    init {
        require(strength in 0.0..1.0) { "Strength must be between 0.0 and 1.0" }
    }

    companion object {
        fun create(type: MatchReasonType, strength: Double, vararg details: Pair<String, String>): MatchReason {
            return MatchReason(
                type = type.name,  // Convert enum to String
                description = type.getDescription(details.toMap()),
                strength = strength,
                details = details.toMap()
            )
        }
    }
}

enum class MatchReasonType {
    SIZE_MATCH,
    ENERGY_MATCH,
    AGE_MATCH,
    BREED_MATCH,
    LOCATION_PROXIMITY,
    TRAINING_LEVEL,
    TEMPERAMENT_MATCH,
    PLAY_STYLE_MATCH,
    SPECIAL_NEEDS_COMPATIBILITY,
    SCHEDULE_COMPATIBILITY;

    fun getDescription(details: Map<String, String> = emptyMap()): String {
        return when (this) {
            SIZE_MATCH -> "Similar size dogs (${details["size1"]} and ${details["size2"]})"
            ENERGY_MATCH -> "Matching energy levels (${details["energy1"]} and ${details["energy2"]})"
            AGE_MATCH -> "Compatible age groups (${details["age1"]} and ${details["age2"]} years)"
            BREED_MATCH -> when {
                details["breed1"] == details["breed2"] -> "Same breed: ${details["breed1"]}"
                else -> "Compatible breeds: ${details["breed1"]} and ${details["breed2"]}"
            }
            LOCATION_PROXIMITY -> {
                val distance = details["distance"]?.toDoubleOrNull()
                when {
                    distance == null -> "Nearby location"
                    distance < 1.0 -> "Less than 1 km away"
                    else -> "About ${String.format("%.1f", distance)} km away"
                }
            }
            TRAINING_LEVEL -> "Similar training levels"
            TEMPERAMENT_MATCH -> "Compatible temperaments"
            PLAY_STYLE_MATCH -> "Similar play styles"
            SPECIAL_NEEDS_COMPATIBILITY -> "Compatible care requirements"
            SCHEDULE_COMPATIBILITY -> "Similar walking schedules"
        }
    }
}

data class MatchReasonGroup(
    @SerializedName("reasons")
    val reasons: List<MatchReason>,

    @SerializedName("totalScore")
    val totalScore: Double = reasons.sumOf { it.strength } / reasons.size,

    @SerializedName("primaryReason")
    val primaryReason: MatchReason? = reasons.maxByOrNull { it.strength }
) {
    fun getFormattedScore(): String = String.format("%.0f%%", totalScore * 100)

    fun getPrimaryReasons(limit: Int = 3): List<MatchReason> {
        return reasons.sortedByDescending { it.strength }.take(limit)
    }

    companion object {
        fun create(vararg reasons: MatchReason): MatchReasonGroup {
            return MatchReasonGroup(reasons.toList())
        }
    }
}

// Extension class for creating match reasons with builder pattern
class MatchReasonBuilder {
    private val reasons = mutableListOf<MatchReason>()

    fun addSizeMatch(size1: String, size2: String, strength: Double) = apply {
        reasons.add(MatchReason.create(
            MatchReasonType.SIZE_MATCH,
            strength,
            "size1" to size1,
            "size2" to size2
        ))
    }

    fun addEnergyMatch(energy1: String, energy2: String, strength: Double) = apply {
        reasons.add(MatchReason.create(
            MatchReasonType.ENERGY_MATCH,
            strength,
            "energy1" to energy1,
            "energy2" to energy2
        ))
    }

    fun addAgeMatch(age1: Int, age2: Int, strength: Double) = apply {
        reasons.add(MatchReason.create(
            MatchReasonType.AGE_MATCH,
            strength,
            "age1" to age1.toString(),
            "age2" to age2.toString()
        ))
    }

    fun addBreedMatch(breed1: String, breed2: String, strength: Double) = apply {
        reasons.add(MatchReason.create(
            MatchReasonType.BREED_MATCH,
            strength,
            "breed1" to breed1,
            "breed2" to breed2
        ))
    }

    fun addLocationProximity(distanceKm: Double, strength: Double) = apply {
        reasons.add(MatchReason.create(
            MatchReasonType.LOCATION_PROXIMITY,
            strength,
            "distance" to distanceKm.toString()
        ))
    }

    fun build(): MatchReasonGroup = MatchReasonGroup(reasons)
}

// Helper class for match scoring
object MatchScoring {
    private const val EXCELLENT_THRESHOLD = 0.8
    private const val GOOD_THRESHOLD = 0.6
    private const val FAIR_THRESHOLD = 0.4

    fun getMatchQuality(score: Double): MatchQuality {
        return when {
            score >= EXCELLENT_THRESHOLD -> MatchQuality.EXCELLENT
            score >= GOOD_THRESHOLD -> MatchQuality.GOOD
            score >= FAIR_THRESHOLD -> MatchQuality.FAIR
            else -> MatchQuality.POOR
        }
    }
}

enum class MatchQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR;

    fun getDescription(): String {
        return when (this) {
            EXCELLENT -> "Perfect Match! 🌟"
            GOOD -> "Good Match 👍"
            FAIR -> "Fair Match"
            POOR -> "Low Compatibility"
        }
    }
}

// Usage example:
/*
val matchReasons = MatchReasonBuilder()
    .addSizeMatch("Medium", "Medium", 1.0)
    .addEnergyMatch("High", "High", 0.9)
    .addAgeMatch(2, 3, 0.8)
    .addLocationProximity(2.5, 0.7)
    .build()

val score = matchReasons.totalScore
val quality = MatchScoring.getMatchQuality(score)
val description = quality.getDescription()
val topReasons = matchReasons.getPrimaryReasons(3)
*/
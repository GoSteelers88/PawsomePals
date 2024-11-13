package io.pawsomepals.app.data.model

import com.google.firebase.database.IgnoreExtraProperties

const val DEFAULT_EXPIRY_DURATION = 7 * 24 * 60 * 60 * 1000L // 7 days in milliseconds

@IgnoreExtraProperties
enum class MatchType(
    val displayName: String,
    val minCompatibilityScore: Double,
    val priorityLevel: Int,
    private val customExpiryDuration: Long = DEFAULT_EXPIRY_DURATION  // renamed to avoid conflict
) {
    NORMAL(
        displayName = "Match",
        minCompatibilityScore = 0.5,
        priorityLevel = 1
    ),

    HIGH_COMPATIBILITY(
        displayName = "High Compatibility Match",
        minCompatibilityScore = 0.8,
        priorityLevel = 2
    ),

    SUPER_LIKE(
        displayName = "Super Like Match",
        minCompatibilityScore = 0.7,
        priorityLevel = 3
    ),

    PERFECT_MATCH(
        displayName = "Perfect Match",
        minCompatibilityScore = 0.95,
        priorityLevel = 4
    ),

    NEARBY(
        displayName = "Nearby Match",
        minCompatibilityScore = 0.6,
        priorityLevel = 2
    ),

    BREED_MATCH(
        displayName = "Breed Match",
        minCompatibilityScore = 0.7,
        priorityLevel = 2
    );

    companion object {
        fun fromCompatibilityScore(
            score: Double,
            distance: Double? = null,
            isSuperLike: Boolean = false,
            isBreedMatch: Boolean = false
        ): MatchType = when {
            score >= PERFECT_MATCH.minCompatibilityScore -> PERFECT_MATCH
            isSuperLike -> SUPER_LIKE
            score >= HIGH_COMPATIBILITY.minCompatibilityScore -> HIGH_COMPATIBILITY
            isBreedMatch -> BREED_MATCH
            distance != null && distance <= 5.0 -> NEARBY // Within 5km
            else -> NORMAL
        }
    }

    fun getMatchMessage(): String = when (this) {
        NORMAL -> "It's a Match!"
        HIGH_COMPATIBILITY -> "Great Match! You're highly compatible!"
        SUPER_LIKE -> "Super Like Match!"
        PERFECT_MATCH -> "Perfect Match! An exceptional connection!"
        NEARBY -> "Nearby Match! A perfect playdate opportunity!"
        BREED_MATCH -> "Breed Match! Similar backgrounds!"
    }

    fun getMatchDescription(): String = when (this) {
        NORMAL -> "You both liked each other"
        HIGH_COMPATIBILITY -> "Your dogs share similar traits and preferences"
        SUPER_LIKE -> "They really liked your profile!"
        PERFECT_MATCH -> "Almost everything aligns perfectly"
        NEARBY -> "A great match right in your neighborhood"
        BREED_MATCH -> "Dogs from similar breeds often get along well"
    }

    fun getExpiryDuration(): Long = when (this) {
        SUPER_LIKE, PERFECT_MATCH -> DEFAULT_EXPIRY_DURATION * 2 // 14 days
        else -> customExpiryDuration  // using the renamed property
    }

    fun shouldShowPriority(): Boolean = when (this) {
        NORMAL -> false
        else -> true
    }

    fun getMatchColor(): String = when (this) {
        NORMAL -> "#4CAF50"         // Green
        HIGH_COMPATIBILITY -> "#2196F3" // Blue
        SUPER_LIKE -> "#9C27B0"     // Purple
        PERFECT_MATCH -> "#FFD700"   // Gold
        NEARBY -> "#00BCD4"         // Cyan
        BREED_MATCH -> "#FF9800"    // Orange
    }
}

@IgnoreExtraProperties
enum class MatchStatus {
    PENDING,    // Initial state when match is created
    ACTIVE,     // Both users have accepted the match
    EXPIRED,    // Match has expired without interaction
    DECLINED,   // One user has declined the match
    CANCELLED   // Match was cancelled after being active
}

@IgnoreExtraProperties
enum class MatchReason(val description: String) {
    BREED_COMPATIBILITY("Similar breeds"),
    AGE_COMPATIBILITY("Similar age"),
    SIZE_COMPATIBILITY("Compatible sizes"),
    ENERGY_LEVEL_MATCH("Matching energy levels"),
    LOCATION_PROXIMITY("Close by"),
    PLAY_STYLE_MATCH("Similar play styles"),
    TRAINING_LEVEL_MATCH("Similar training levels"),
    TEMPERAMENT_MATCH("Compatible temperaments"),
    HEALTH_COMPATIBILITY("Health compatible"),
    SOCIAL_COMPATIBILITY("Social compatibility")
}

// Extension functions for UI display
fun MatchType.getMatchIcon(): String = when (this) {
    MatchType.NORMAL -> "🐾"
    MatchType.HIGH_COMPATIBILITY -> "⭐"
    MatchType.SUPER_LIKE -> "💫"
    MatchType.PERFECT_MATCH -> "👑"
    MatchType.NEARBY -> "📍"
    MatchType.BREED_MATCH -> "🏆"
}

fun MatchStatus.getStatusColor(): String = when (this) {
    MatchStatus.PENDING -> "#FFC107"    // Amber
    MatchStatus.ACTIVE -> "#4CAF50"     // Green
    MatchStatus.EXPIRED -> "#9E9E9E"    // Grey
    MatchStatus.DECLINED -> "#F44336"   // Red
    MatchStatus.CANCELLED -> "#FF5722"  // Deep Orange
}

fun MatchStatus.getStatusIcon(): String = when (this) {
    MatchStatus.PENDING -> "⌛"
    MatchStatus.ACTIVE -> "✓"
    MatchStatus.EXPIRED -> "⏰"
    MatchStatus.DECLINED -> "✕"
    MatchStatus.CANCELLED -> "⭕"
}

fun MatchReason.getReasonIcon(): String = when (this) {
    MatchReason.BREED_COMPATIBILITY -> "🏆"
    MatchReason.AGE_COMPATIBILITY -> "🎂"
    MatchReason.SIZE_COMPATIBILITY -> "📏"
    MatchReason.ENERGY_LEVEL_MATCH -> "⚡"
    MatchReason.LOCATION_PROXIMITY -> "📍"
    MatchReason.PLAY_STYLE_MATCH -> "🎮"
    MatchReason.TRAINING_LEVEL_MATCH -> "🎓"
    MatchReason.TEMPERAMENT_MATCH -> "😊"
    MatchReason.HEALTH_COMPATIBILITY -> "❤️"
    MatchReason.SOCIAL_COMPATIBILITY -> "👥"
}
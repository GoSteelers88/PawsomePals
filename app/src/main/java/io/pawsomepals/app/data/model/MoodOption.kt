package io.pawsomepals.app.data.model

// MoodOption.kt
data class MoodOption(
    val text: String,
    val mood: PlaydateMode,
    val icon: String
)

enum class PlaydateMode {
    FETCH,
    HIGH_ENERGY,
    CALM_WALK,
    SOCIAL
}


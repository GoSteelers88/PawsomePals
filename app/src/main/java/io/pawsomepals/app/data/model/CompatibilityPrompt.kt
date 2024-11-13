package io.pawsomepals.app.data.model



import java.util.UUID

// CompatibilityPrompt.kt
data class CompatibilityPrompt(
    val id: String = UUID.randomUUID().toString(),

    val message: String,
    val type: PromptType
)

enum class PromptType {
    BREED_INFO,
    TRAINING_TIP,
    HEALTH_TIP,
    PLAY_STYLE
}
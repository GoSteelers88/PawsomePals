package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dateEarned: Long = System.currentTimeMillis(),
    val icon: String? = null,
    val type: AchievementType = AchievementType.OTHER,
    val dogId: String = "",  // Reference to the dog who earned it
    val metadata: Map<String, Any>? = null  // Additional achievement data
)

enum class AchievementType {
    SOCIALIZATION {
        override fun getIcon() = "🐾"
        override fun getDescription() = "Social butterfly achievements"
    },
    TRAINING {
        override fun getIcon() = "🎓"
        override fun getDescription() = "Training milestones"
    },
    PLAYDATE {
        override fun getIcon() = "🎯"
        override fun getDescription() = "Playdate accomplishments"
    },
    EXERCISE {
        override fun getIcon() = "⚡"
        override fun getDescription() = "Exercise goals"
    },
    MILESTONE {
        override fun getIcon() = "🏆"
        override fun getDescription() = "Platform milestones"
    },
    OTHER {
        override fun getIcon() = "✨"
        override fun getDescription() = "Special achievements"
    };

    abstract fun getIcon(): String
    abstract fun getDescription(): String
}



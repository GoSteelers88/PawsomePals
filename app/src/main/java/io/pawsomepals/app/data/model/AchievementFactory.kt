
package io.pawsomepals.app.data.model

import java.util.UUID

/**
 * Factory class to create standardized achievements for the io.pawsomepals.app.
 * Uses object (Singleton) to ensure consistency across the io.pawsomepals.app.
 */
object AchievementFactory {

    // Playdate Achievements
    fun createPlaydateAchievement(dogId: String, playdateCount: Int): Achievement {
        return when (playdateCount) {
            1 -> Achievement(
                id = generateId("first_playdate", dogId),
                title = "First Playdate!",
                description = "Successfully completed your first playdate",
                type = AchievementType.PLAYDATE,
                dogId = dogId,
                icon = "🎯",
                metadata = mapOf(
                    "playdateCount" to 1,
                    "milestone" to "first"
                )
            )
            5 -> Achievement(
                id = generateId("playdate_pro", dogId),
                title = "Playdate Pro",
                description = "Completed 5 successful playdates",
                type = AchievementType.PLAYDATE,
                icon = "🌟",
                metadata = mapOf("playdateCount" to 5)
            )
            10 -> Achievement(
                id = generateId("playdate_master", dogId),
                title = "Playdate Master",
                description = "Completed 10 successful playdates",
                type = AchievementType.PLAYDATE,
                icon = "👑",
                metadata = mapOf("playdateCount" to 10)
            )
            else -> Achievement(
                id = generateId("playdate_milestone", dogId),
                title = "Playdate Milestone",
                description = "Completed $playdateCount playdates",
                type = AchievementType.PLAYDATE,
                metadata = mapOf("playdateCount" to playdateCount)
            )
        }
    }

    // Training Achievements
    fun createTrainingAchievement(dogId: String, level: Int, certification: String? = null): Achievement {
        return Achievement(
            id = generateId("training_level_$level", dogId),
            title = "Training Level $level Mastered",
            description = certification?.let { "Earned certification in $it" }
                ?: "Completed training level $level",
            type = AchievementType.TRAINING,
            dogId = dogId,
            icon = "🎓",
            metadata = mapOf(
                "level" to level,
                "certification" to (certification ?: "none")
            )
        )
    }

    // Social Achievements
    fun createSocialAchievement(dogId: String, friendCount: Int): Achievement {
        return when (friendCount) {
            1 -> Achievement(
                id = generateId("first_friend", dogId),
                title = "First Friend",
                description = "Made your first friend on PawsomePals",
                type = AchievementType.SOCIALIZATION,
                dogId = dogId,
                icon = "🐾",
                metadata = mapOf("friendCount" to 1)
            )
            5 -> Achievement(
                id = generateId("social_butterfly", dogId),
                title = "Social Butterfly",
                description = "Made friends with 5 dogs",
                type = AchievementType.SOCIALIZATION,
                dogId = dogId,
                icon = "🦋",
                metadata = mapOf("friendCount" to 5)
            )
            10 -> Achievement(
                id = generateId("pack_leader", dogId),
                title = "Pack Leader",
                description = "Made friends with 10 dogs",
                type = AchievementType.SOCIALIZATION,
                dogId = dogId,
                icon = "🐺",
                metadata = mapOf("friendCount" to 10)
            )
            else -> Achievement(
                id = generateId("friend_milestone", dogId),
                title = "Friendship Milestone",
                description = "Made friends with $friendCount dogs",
                type = AchievementType.SOCIALIZATION,
                dogId = dogId,
                metadata = mapOf("friendCount" to friendCount)
            )
        }
    }

    // Exercise Achievements
    fun createExerciseAchievement(
        dogId: String,
        walkCount: Int,
        totalDistance: Double? = null
    ): Achievement {
        return when (walkCount) {
            10 -> Achievement(
                id = generateId("walk_enthusiast", dogId),
                title = "Walk Enthusiast",
                description = totalDistance?.let {
                    "Completed 10 walks (${String.format("%.1f", totalDistance)}km)"
                } ?: "Completed 10 walks",
                type = AchievementType.EXERCISE,
                dogId = dogId,
                icon = "🦮",
                metadata = mapOf(
                    "walkCount" to walkCount,
                    "totalDistance" to (totalDistance?.toString() ?: "0.0")  // Convert to String
                )
            )
            50 -> Achievement(
                id = generateId("walk_master", dogId),
                title = "Walk Master",
                description = totalDistance?.let {
                    "Completed 50 walks (${String.format("%.1f", totalDistance)}km)"
                } ?: "Completed 50 walks",
                type = AchievementType.EXERCISE,
                dogId = dogId,
                icon = "🏃",
                metadata = mapOf(
                    "walkCount" to walkCount,
                    "totalDistance" to (totalDistance?.toString() ?: "0.0")  // Convert to String
                )
            )
            else -> Achievement(
                id = generateId("walk_milestone", dogId),
                title = "Walking Milestone",
                description = "Completed $walkCount walks",
                type = AchievementType.EXERCISE,
                dogId = dogId,
                metadata = mapOf("walkCount" to walkCount)
            )
        }
    }

    // Platform Milestone Achievements
    fun createMilestoneAchievement(dogId: String, daysOnPlatform: Int): Achievement {
        return when (daysOnPlatform) {
            7 -> Achievement(
                id = generateId("week_milestone", dogId),
                title = "One Week Wonder",
                description = "Been a member for a week",
                type = AchievementType.MILESTONE,
                dogId = dogId,
                icon = "🎉",
                metadata = mapOf("days" to 7)
            )
            30 -> Achievement(
                id = generateId("month_milestone", dogId),
                title = "Monthly Member",
                description = "Been a member for a month",
                type = AchievementType.MILESTONE,
                dogId = dogId,
                icon = "📅",
                metadata = mapOf("days" to 30)
            )
            365 -> Achievement(
                id = generateId("year_milestone", dogId),
                title = "Year-Long Companion",
                description = "Been a member for a year",
                type = AchievementType.MILESTONE,
                dogId = dogId,
                icon = "🎊",
                metadata = mapOf("days" to 365)
            )
            else -> Achievement(
                id = generateId("time_milestone", dogId),
                title = "Time Milestone",
                description = "Been a member for $daysOnPlatform days",
                type = AchievementType.MILESTONE,
                dogId = dogId,
                metadata = mapOf("days" to daysOnPlatform)
            )
        }
    }

    // Special Achievements
    fun createSpecialAchievement(
        dogId: String,
        title: String,
        description: String,
        icon: String? = null,
        metadata: Map<String, Any>? = null
    ): Achievement {
        return Achievement(
            id = generateId("special", dogId),
            title = title,
            description = description,
            type = AchievementType.OTHER,
            dogId = dogId,
            icon = icon,
            metadata = metadata
        )
    }

    // Helper function to generate consistent IDs
    private fun generateId(type: String, dogId: String): String {
        return "${type}_${dogId}_${UUID.randomUUID()}"
    }

    // Helper function to check if achievement should be awarded
    fun shouldAwardAchievement(type: String, value: Int): Boolean {
        return when (type) {
            "playdate" -> value in listOf(1, 5, 10, 25, 50, 100)
            "friend" -> value in listOf(1, 5, 10, 25, 50)
            "walk" -> value in listOf(10, 25, 50, 100, 200)
            "days" -> value in listOf(7, 30, 90, 180, 365)
            else -> false
        }
    }
}


package io.pawsomepals.app.service

import android.util.Log
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.model.Achievement
import io.pawsomepals.app.data.model.AchievementFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementService @Inject constructor(
    private val dataManager: DataManager,
    private val firestore: FirebaseFirestore
) {
    suspend fun checkAndAwardPlaydateAchievements(dogId: String) {
        try {
            // Get completed playdate count
            val playdateCount = getCompletedPlaydateCount(dogId)

            if (AchievementFactory.shouldAwardAchievement("playdate", playdateCount)) {
                val achievement = AchievementFactory.createPlaydateAchievement(
                    dogId = dogId,
                    playdateCount = playdateCount
                )
                awardAchievement(achievement)
            }
        } catch (e: Exception) {
            Log.e("AchievementService", "Error checking playdate achievements", e)
        }
    }

    suspend fun unlockAchievement(dogId: String, achievementId: String) {
        try {
            // Determine achievement type from ID
            val achievement = when {
                achievementId.startsWith("playdate") -> {
                    val count = getCompletedPlaydateCount(dogId)
                    AchievementFactory.createPlaydateAchievement(dogId, count)
                }
                achievementId.startsWith("social") -> {
                    val count = getFriendCount(dogId)
                    AchievementFactory.createSocialAchievement(dogId, count)
                }
                achievementId.startsWith("milestone") -> {
                    val days = getDaysOnPlatform(dogId)
                    AchievementFactory.createMilestoneAchievement(dogId, days)
                }
                else -> {
                    AchievementFactory.createSpecialAchievement(
                        dogId = dogId,
                        title = "Special Achievement",
                        description = "Unlocked a special achievement",
                        icon = "🏆"
                    )
                }
            }
            awardAchievement(achievement)
        } catch (e: Exception) {
            Log.e("AchievementService", "Error unlocking achievement", e)
        }
    }


    suspend fun checkAndAwardSocialAchievements(dogId: String) {
        try {
            val friendCount = getFriendCount(dogId)

            if (AchievementFactory.shouldAwardAchievement("friend", friendCount)) {
                val achievement = AchievementFactory.createSocialAchievement(
                    dogId = dogId,
                    friendCount = friendCount
                )
                awardAchievement(achievement)
            }
        } catch (e: Exception) {
            Log.e("AchievementService", "Error checking social achievements", e)
        }
    }

    suspend fun checkAndAwardMilestoneAchievements(dogId: String) {
        try {
            val daysOnPlatform = getDaysOnPlatform(dogId)

            if (AchievementFactory.shouldAwardAchievement("days", daysOnPlatform)) {
                val achievement = AchievementFactory.createMilestoneAchievement(
                    dogId = dogId,
                    daysOnPlatform = daysOnPlatform
                )
                awardAchievement(achievement)
            }
        } catch (e: Exception) {
            Log.e("AchievementService", "Error checking milestone achievements", e)
        }
    }

    private suspend fun awardAchievement(achievement: Achievement) {
        try {
            // Check if achievement already exists
            val exists = checkAchievementExists(achievement.dogId, achievement.id)
            if (!exists) {
                // Save to Firestore
                firestore.collection("achievements")
                    .document(achievement.id)
                    .set(achievement)
                    .await()

                // Update local database through DataManager
                dataManager.saveAchievement(achievement)

                // Could add notification or UI update here
                Log.d("AchievementService", "Achievement awarded: ${achievement.title}")
            }
        } catch (e: Exception) {
            Log.e("AchievementService", "Error awarding achievement", e)
        }
    }

    private suspend fun checkAchievementExists(dogId: String, achievementId: String): Boolean {
        return try {
            val doc = firestore.collection("achievements")
                .document(achievementId)
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            Log.e("AchievementService", "Error checking achievement existence", e)
            false
        }
    }

    private suspend fun getCompletedPlaydateCount(dogId: String): Int {
        return try {
            firestore.collection("playdates")
                .whereEqualTo("dogId", dogId)
                .whereEqualTo("status", "COMPLETED")
                .get()
                .await()
                .size()
        } catch (e: Exception) {
            Log.e("AchievementService", "Error getting playdate count", e)
            0
        }
    }

    private suspend fun getFriendCount(dogId: String): Int {
        return try {
            firestore.collection("matches")
                .whereArrayContains("dogIds", dogId)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()
                .size()
        } catch (e: Exception) {
            Log.e("AchievementService", "Error getting friend count", e)
            0
        }
    }

    private suspend fun getDaysOnPlatform(dogId: String): Int {
        return try {
            val dog = firestore.collection("dogs")
                .document(dogId)
                .get()
                .await()

            val creationTime = dog.getLong("created") ?: return 0
            val now = System.currentTimeMillis()
            val diffInMillis = now - creationTime
            TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        } catch (e: Exception) {
            Log.e("AchievementService", "Error calculating days on platform", e)
            0
        }
    }
}
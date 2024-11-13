package io.pawsomepals.app.service

import android.os.Bundle
import android.util.Log
import io.pawsomepals.app.data.model.PlaydateMood
import io.pawsomepals.app.data.model.PromptType
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsService @Inject constructor() {
    private val analytics = Firebase.analytics

    // Custom Events
    private object Events {
        const val PROMPT_SELECTED = "prompt_selected"
        const val PLAYDATE_SCHEDULED = "playdate_scheduled"
        const val PLAYDATE_COMPLETED = "playdate_completed"
        const val MATCH_CREATED = "match_created"
        const val ACHIEVEMENT_EARNED = "achievement_earned"
        const val APP_ERROR = "app_error"
        const val SWIPE_ACTION = "swipe_action"
        const val MESSAGE_SENT = "message_sent"
        const val DOG_PROFILE_CREATED = "dog_profile_created"
        const val DOG_PROFILE_UPDATED = "dog_profile_updated"
    }

    // Custom Parameters
    private object Params {
        const val PROMPT_TYPE = "prompt_type"
        const val LOCATION = "location"
        const val DURATION = "duration_minutes"
        const val COMPATIBILITY_SCORE = "compatibility_score"
        const val ERROR_TYPE = "error_type"
        const val ERROR_MESSAGE = "error_message"
        const val SWIPE_DIRECTION = "swipe_direction"
        const val MESSAGE_TYPE = "message_type"
        const val DOG_BREED = "dog_breed"
        const val DOG_AGE = "dog_age"
        const val PLAYDATE_MOOD = "playdate_mood"
    }

    fun trackPromptUsage(promptType: PromptType) {
        try {
            val bundle = Bundle().apply {
                putString(Params.PROMPT_TYPE, promptType.name)
            }
            analytics.logEvent(Events.PROMPT_SELECTED, bundle)
        } catch (e: Exception) {
            logError("trackPromptUsage", e)
        }
    }

    fun trackPlaydateScheduled(
        playdateId: String,
        locationName: String,
        distance: Double? = null
    ) {
        try {
            val bundle = Bundle().apply {
                putString("playdate_id", playdateId)
                putString(Params.LOCATION, locationName)
                distance?.let { putDouble("distance", it) }
            }
            analytics.logEvent(Events.PLAYDATE_SCHEDULED, bundle)
        } catch (e: Exception) {
            logError("trackPlaydateScheduled", e)
        }
    }

    fun trackPlaydateCompleted(
        playdateId: String,
        duration: Long,
        mood: PlaydateMood
    ) {
        try {
            val bundle = Bundle().apply {
                putString("playdate_id", playdateId)
                putLong(Params.DURATION, duration)
                putString(Params.PLAYDATE_MOOD, mood.name)
            }
            analytics.logEvent(Events.PLAYDATE_COMPLETED, bundle)
        } catch (e: Exception) {
            logError("trackPlaydateCompleted", e)
        }
    }

    fun trackMatchCreated(
        matchId: String,
        compatibilityScore: Double,
        matchReasons: List<String>
    ) {
        try {
            val bundle = Bundle().apply {
                putString("match_id", matchId)
                putDouble(Params.COMPATIBILITY_SCORE, compatibilityScore)
                putString("match_reasons", matchReasons.joinToString(","))
            }
            analytics.logEvent(Events.MATCH_CREATED, bundle)
        } catch (e: Exception) {
            logError("trackMatchCreated", e)
        }
    }

    fun trackAchievementEarned(achievementId: String, achievementName: String) {
        try {
            val bundle = Bundle().apply {
                putString("achievement_id", achievementId)
                putString("achievement_name", achievementName)
            }
            analytics.logEvent(Events.ACHIEVEMENT_EARNED, bundle)
        } catch (e: Exception) {
            logError("trackAchievementEarned", e)
        }
    }

    fun trackDogProfileCreated(
        dogId: String,
        breed: String,
        age: Int,
        metadata: Map<String, Any>? = null
    ) {
        try {
            val bundle = Bundle().apply {
                putString("dog_id", dogId)
                putString(Params.DOG_BREED, breed)
                putInt(Params.DOG_AGE, age)
                metadata?.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                    }
                }
            }
            analytics.logEvent(Events.DOG_PROFILE_CREATED, bundle)
        } catch (e: Exception) {
            logError("trackDogProfileCreated", e)
        }
    }

    fun trackMessageSent(
        chatId: String,
        messageType: String,
        hasMedia: Boolean = false
    ) {
        try {
            val bundle = Bundle().apply {
                putString("chat_id", chatId)
                putString(Params.MESSAGE_TYPE, messageType)
                putBoolean("has_media", hasMedia)
            }
            analytics.logEvent(Events.MESSAGE_SENT, bundle)
        } catch (e: Exception) {
            logError("trackMessageSent", e)
        }
    }

    fun trackSwipeAction(
        swiperId: String,
        swipedId: String,
        direction: String,
        reason: String? = null
    ) {
        try {
            val bundle = Bundle().apply {
                putString("swiper_id", swiperId)
                putString("swiped_id", swipedId)
                putString(Params.SWIPE_DIRECTION, direction)
                reason?.let { putString("swipe_reason", it) }
            }
            analytics.logEvent(Events.SWIPE_ACTION, bundle)
        } catch (e: Exception) {
            logError("trackSwipeAction", e)
        }
    }

    fun trackError(errorType: String, errorMessage: String, stackTrace: String? = null) {
        try {
            val bundle = Bundle().apply {
                putString(Params.ERROR_TYPE, errorType)
                putString(Params.ERROR_MESSAGE, errorMessage)
                stackTrace?.let { putString("stack_trace", it) }
            }
            analytics.logEvent(Events.APP_ERROR, bundle)
        } catch (e: Exception) {
            logError("trackError", e)
        }
    }

    private fun logError(methodName: String, error: Exception) {
        Log.e(
            "AnalyticsService",
            "Error in $methodName: ${error.message}",
            error
        )
    }

    // Helper function to safely track custom events
    fun trackCustomEvent(eventName: String, params: Map<String, Any>? = null) {
        try {
            val bundle = Bundle().apply {
                params?.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                    }
                }
            }
            analytics.logEvent(eventName, bundle)
        } catch (e: Exception) {
            logError("trackCustomEvent", e)
        }
    }

    // Helper function to set user properties
    fun setUserProperty(name: String, value: String?) {
        try {
            analytics.setUserProperty(name, value)
        } catch (e: Exception) {
            logError("setUserProperty", e)
        }
    }
}
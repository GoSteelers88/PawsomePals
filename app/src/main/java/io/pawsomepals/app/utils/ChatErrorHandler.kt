package io.pawsomepals.app.utils

import android.content.Context
import io.pawsomepals.app.data.model.error.ChatError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatErrorHandler @Inject constructor(
    private val context: Context
) {
    fun getErrorMessage(error: ChatError): String {
        return when (error) {
            is ChatError.MessageSendFailure -> "Failed to send message. ${error.message}"
            is ChatError.MessageNotFound -> "Message not found"
            is ChatError.MessageDeliveryFailed -> "Message couldn't be delivered"
            is ChatError.ChatNotFound -> "Chat conversation not found"
            is ChatError.ChatCreationFailed -> "Couldn't create chat. ${error.message}"
            is ChatError.UnauthorizedAccess -> "You don't have permission to access this chat"
            is ChatError.NetworkError -> "Network error. Please check your connection"
            is ChatError.StorageError -> "Storage error. ${error.message}"
            is ChatError.ValidationError -> getValidationErrorMessage(error)
            is ChatError.RateLimitExceeded -> getRateLimitMessage(error)
            is ChatError.ContentError -> "Invalid content: ${error.message}"
            is ChatError.PlaydateError.SchedulingError -> "Couldn't schedule playdate: ${error.message}"
            is ChatError.PlaydateError.LocationError -> "Location error: ${error.message}"
            is ChatError.PlaydateError.TimeSlotUnavailable -> "This time slot is no longer available"
        }
    }

    fun getErrorAction(error: ChatError): ErrorAction {
        return when (error) {
            is ChatError.NetworkError -> ErrorAction.RETRY
            is ChatError.MessageDeliveryFailed -> ErrorAction.RESEND
            is ChatError.RateLimitExceeded -> ErrorAction.WAIT
            is ChatError.UnauthorizedAccess -> ErrorAction.SIGN_IN
            else -> ErrorAction.DISMISS
        }
    }

    private fun getValidationErrorMessage(error: ChatError.ValidationError): String {
        return when (error.field) {
            "content" -> "Message cannot be empty"
            "length" -> "Message is too long"
            else -> error.message
        }
    }

    private fun getRateLimitMessage(error: ChatError.RateLimitExceeded): String {
        return error.retryAfterSeconds?.let {
            "Please wait $it seconds before sending another message"
        } ?: "You're sending messages too quickly"
    }

    fun shouldRetry(error: ChatError): Boolean {
        return when (error) {
            is ChatError.NetworkError,
            is ChatError.MessageDeliveryFailed,
            is ChatError.StorageError -> true
            else -> false
        }
    }

    fun getRetryDelay(error: ChatError, attempt: Int): Long {
        return when (error) {
            is ChatError.RateLimitExceeded -> error.retryAfterSeconds?.times(1000L) ?: 5000L
            else -> calculateExponentialBackoff(attempt)
        }
    }

    private fun calculateExponentialBackoff(attempt: Int): Long {
        return minOf(
            MAX_BACKOFF_MS,
            BASE_BACKOFF_MS * (1L shl minOf(attempt, 5))
        )
    }

    companion object {
        private const val BASE_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 32000L
    }
}

enum class ErrorAction {
    RETRY,
    RESEND,
    WAIT,
    SIGN_IN,
    DISMISS
}

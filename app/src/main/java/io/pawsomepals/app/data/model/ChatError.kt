package io.pawsomepals.app.data.model.error

sealed class ChatError : Exception() {
    // Message Errors
    data class MessageSendFailure(
        override val message: String,
        override val cause: Throwable? = null
    ) : ChatError()

    data class MessageNotFound(
        val messageId: String,
        override val message: String = "Message not found: $messageId"
    ) : ChatError()

    data class MessageDeliveryFailed(
        val messageId: String,
        override val message: String = "Failed to deliver message: $messageId",
        override val cause: Throwable? = null
    ) : ChatError()

    // Chat Room Errors
    data class ChatNotFound(
        val chatId: String,
        override val message: String = "Chat room not found: $chatId"
    ) : ChatError()

    data class ChatCreationFailed(
        override val message: String,
        override val cause: Throwable? = null
    ) : ChatError()

    // Permission Errors
    data class UnauthorizedAccess(
        val chatId: String,
        override val message: String = "Unauthorized access to chat: $chatId"
    ) : ChatError()

    // Network Errors
    data class NetworkError(
        override val message: String = "Network error occurred",
        override val cause: Throwable? = null
    ) : ChatError()

    // Storage Errors
    data class StorageError(
        override val message: String,
        override val cause: Throwable? = null
    ) : ChatError()

    // Validation Errors
    data class ValidationError(
        override val message: String,
        val field: String? = null
    ) : ChatError()

    // Rate Limiting
    data class RateLimitExceeded(
        override val message: String = "Message rate limit exceeded",
        val retryAfterSeconds: Int? = null
    ) : ChatError()

    // Content Errors
    data class ContentError(
        override val message: String,
        val contentType: String? = null
    ) : ChatError()

    // Playdate-specific Errors
    sealed class PlaydateError : ChatError() {
        data class SchedulingError(
            override val message: String,
            val playdateId: String? = null
        ) : PlaydateError()

        data class LocationError(
            override val message: String,
            val location: String? = null
        ) : PlaydateError()

        data class TimeSlotUnavailable(
            override val message: String = "Selected time slot is no longer available"
        ) : PlaydateError()
    }
}

// Extension function to convert exceptions to ChatError
fun Throwable.toChatError(): ChatError {
    return when (this) {
        is ChatError -> this
        is java.net.UnknownHostException,
        is java.net.SocketTimeoutException,
        is java.io.IOException -> ChatError.NetworkError(cause = this)
        else -> ChatError.MessageSendFailure(
            message = this.message ?: "Unknown error occurred",
            cause = this
        )
    }
}

// Result wrapper for chat operations
sealed class ChatResult<out T> {
    data class Success<T>(val data: T) : ChatResult<T>()
    data class Error(val error: ChatError) : ChatResult<Nothing>()
}

// Extension function to handle chat results
inline fun <T> ChatResult<T>.onSuccess(action: (T) -> Unit): ChatResult<T> {
    if (this is ChatResult.Success) {
        action(data)
    }
    return this
}

inline fun <T> ChatResult<T>.onError(action: (ChatError) -> Unit): ChatResult<T> {
    if (this is ChatResult.Error) {
        action(error)
    }
    return this
}

// Helper function to wrap chat operations in ChatResult
suspend fun <T> runChatCatching(block: suspend () -> T): ChatResult<T> {
    return try {
        ChatResult.Success(block())
    } catch (e: Exception) {
        ChatResult.Error(e.toChatError())
    }
}

// RequestStatus.kt
package io.pawsomepals.app.data.model

// For API/Network responses and UI states
sealed class RequestState<out T> {
    object Initial : RequestState<Nothing>()
    object Loading : RequestState<Nothing>()
    data class Success<T>(val data: T) : RequestState<T>()
    data class Error(val message: String) : RequestState<Nothing>()
}

// For database entity
enum class RequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    RESCHEDULED,
    CANCELED
}
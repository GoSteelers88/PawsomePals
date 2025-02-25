package io.pawsomepals.app.exception

/**
 * Custom exceptions for handling specific error cases in the authentication and registration flow.
 */

/**
 * Thrown when attempting to register a user with an email that already exists.
 * @param message Detailed error message explaining the duplicate user condition
 */
class UserAlreadyExistsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Thrown when authentication-related operations fail.
 * This could include invalid credentials, expired tokens, or missing authentication state.
 * @param message Detailed error message explaining the authentication failure
 */
class AuthenticationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        private const val serialVersionUID = 2L
    }
}

/**
 * Thrown when a user document cannot be found in the database when it's expected to exist.
 * @param message Detailed error message explaining which user was not found and in what context
 */
class UserNotFoundException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        private const val serialVersionUID = 3L
    }
}

/**
 * Generic exception for registration-related failures.
 * This is typically used as a wrapper for other exceptions that occur during the registration process.
 * @param message Detailed error message explaining the registration failure
 * @param cause The underlying exception that caused the registration to fail
 */
class RegistrationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        private const val serialVersionUID = 4L
    }

    /**
     * Additional constructor for cases where we want to include error details
     */
    constructor(
        message: String,
        errorDetails: Map<String, Any>,
        cause: Throwable? = null
    ) : this("$message Details: $errorDetails", cause)
}

/**
 * Extension function to create a user-friendly error message
 */
fun Exception.toUserFriendlyMessage(): String = when (this) {
    is UserAlreadyExistsException -> "An account with this email already exists."
    is AuthenticationException -> "Authentication failed. Please try again."
    is UserNotFoundException -> "User account not found."
    is RegistrationException -> "Registration failed. Please try again."
    else -> "An unexpected error occurred. Please try again."
}
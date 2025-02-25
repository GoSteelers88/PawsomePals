package io.pawsomepals.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val dataManager: DataManager,


) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun registerUser(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        petName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                if (!validateInputs(username, email, password, confirmPassword)) {
                    return@launch
                }

                authRepository.createUserInFirestore(
                    email = email,
                    password = password,
                    username = username,
                    petName = petName
                ).fold(
                    onSuccess = { onSuccess() },
                    onFailure = { handleRegistrationError(it, onError) }
                )
            } catch (e: Exception) {
                handleRegistrationError(e, onError)
            } finally {
                _isLoading.value = false
            }
        }
    }
    private fun handleRegistrationError(e: Throwable, onError: (String) -> Unit) {
        val errorMessage = when {
            e.message?.contains("email already in use", ignoreCase = true) == true ->
                "This email is already registered"
            e.message?.contains("weak password", ignoreCase = true) == true ->
                "Password must be at least 6 characters long"
            e.message?.contains("invalid email", ignoreCase = true) == true ->
                "Please enter a valid email address"
            else -> e.message ?: "Registration failed"
        }
        _error.value = errorMessage
        onError(errorMessage)
    }


    private fun validateInputs(
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (username.isBlank()) {
            _error.value = "Username cannot be empty"
            return false
        }

        if (email.isBlank()) {
            _error.value = "Email cannot be empty"
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _error.value = "Please enter a valid email address"
            return false
        }

        if (password.length < 6) {
            _error.value = "Password must be at least 6 characters long"
            return false
        }

        if (password != confirmPassword) {
            _error.value = "Passwords do not match"
            return false
        }

        return true
    }

    fun clearError() {
        _error.value = null
    }
}
package com.example.pawsomepals.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    fun registerUser(username: String, email: String, password: String, confirmPassword: String, petName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if (password != confirmPassword) {
                onError("Passwords do not match")
                return@launch
            }

            try {
                userRepository.registerUser(username, email, password, petName)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Registration failed")
            }
        }
    }

    fun registerUser(username: String, email: String, password: String, petName: String?,
                     onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("RegisterViewModel", "Attempting to register user: $username")
                // Assuming userRepository.registerUser is a suspend function
                userRepository.registerUser(username, email, password, petName)
                Log.d("RegisterViewModel", "User registered successfully: $username")
                onSuccess()
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "Registration failed", e)
                onError(e.message ?: "An unknown error occurred")
            }
        }
    }

    class Factory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RegisterViewModel(userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
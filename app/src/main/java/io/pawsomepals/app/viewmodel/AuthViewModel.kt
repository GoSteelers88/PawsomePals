package io.pawsomepals.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authStateManager: AuthStateManager,
    private val userRepository: UserRepository
) : ViewModel() {

    val authState = authStateManager.authState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AuthStateManager.AuthState.Initial
    )
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isLoading = authStateManager.isLoading
    val isUserFullyLoaded = authStateManager.isUserFullyLoaded

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun registerUser(email: String, password: String, username: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                authStateManager.createUserWithEmail(email, password, username)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                authStateManager.signInWithEmail(email, password)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }


    suspend fun updateUserTermsStatus(userId: String, accepted: Boolean) {
        userRepository.updateUserTermsStatus(userId, accepted)
    }

    fun logOut() {
        viewModelScope.launch {
            try {
                authStateManager.signOut()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
}
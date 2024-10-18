package com.example.pawsomepals.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pawsomepals.data.repository.AuthRepository
import com.example.pawsomepals.data.repository.ChatRepository
import com.example.pawsomepals.data.repository.OpenAIRepository
import com.example.pawsomepals.data.repository.PlaydateRepository
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.notification.NotificationManager
import com.example.pawsomepals.service.LocationService
import com.example.pawsomepals.service.LocationSuggestionService
import com.example.pawsomepals.service.MatchingService
import com.example.pawsomepals.utils.RecaptchaManager
import com.facebook.CallbackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import com.example.pawsomepals.auth.GoogleAuthManager


@Singleton
class ViewModelFactory @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val playdateRepository: PlaydateRepository,
    private val openAIRepository: OpenAIRepository,
    private val locationService: LocationService,
    private val matchingService: MatchingService,
    private val notificationManager: NotificationManager,
    private val locationSuggestionService: LocationSuggestionService,
    private val authRepository: AuthRepository,
    private val recaptchaManager: RecaptchaManager,
    private val facebookCallbackManager: CallbackManager,
    private val application: Application,
    @ApplicationContext private val context: Context,
    private val googleAuthManager: GoogleAuthManager


) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(
                    userRepository,
                    authRepository,
                    recaptchaManager,
                    facebookCallbackManager,
                    context,
                    googleAuthManager

                ) as T

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(userRepository, locationService) as T
            // Add other ViewModels here...
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
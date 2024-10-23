package com.example.pawsomepals.viewmodel

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.pawsomepals.auth.GoogleAuthManager
import com.example.pawsomepals.data.DataManager
import com.example.pawsomepals.data.repository.*
import com.example.pawsomepals.notification.NotificationManager
import com.example.pawsomepals.service.LocationService
import com.example.pawsomepals.service.LocationSuggestionService
import com.example.pawsomepals.service.MatchingService
import com.example.pawsomepals.utils.RecaptchaManager
import com.facebook.CallbackManager
import com.google.firebase.storage.FirebaseStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext

class ViewModelFactory @AssistedInject constructor(
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
    private val googleAuthManager: GoogleAuthManager,
    private val storage: FirebaseStorage,
    private val dataManager: DataManager,
    @Assisted private val owner: ComponentActivity
) : AbstractSavedStateViewModelFactory(owner, null) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return when {
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(
                    userRepository,
                    locationService,
                    storage,
                    dataManager,
                    handle
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(owner: ComponentActivity): ViewModelFactory
    }
}
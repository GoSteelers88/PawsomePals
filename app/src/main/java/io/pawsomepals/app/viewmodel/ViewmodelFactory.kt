package io.pawsomepals.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.facebook.CallbackManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.OpenAIRepository
import io.pawsomepals.app.data.repository.PhotoRepository
import io.pawsomepals.app.data.repository.PlaydateRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.utils.CameraManager
import io.pawsomepals.app.utils.NetworkUtils
import io.pawsomepals.app.utils.RecaptchaManager

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
    private val networkUtils: NetworkUtils,
    private val cameraManager: CameraManager, // Add this
    private val photoRepository: PhotoRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val firebaseAuth: FirebaseAuth,
    private val authStateManager: AuthStateManager,  // Add this
// Add this
// Add this
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
                    application = application,
                    userRepository = userRepository,
                    locationService = locationService,
                    storage = storage,
                    dataManager = dataManager,
                    savedStateHandle = handle,
                    networkUtils = networkUtils,
                    cameraManager = cameraManager,
                    photoRepository = photoRepository,
                    dogProfileRepository = dogProfileRepository,
                    firebaseAuth = firebaseAuth,
                    authStateManager = authStateManager



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
package io.pawsomepals.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.repository.*
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.utils.ImageHandler
import io.pawsomepals.app.utils.NetworkUtils
import io.pawsomepals.app.utils.RecaptchaManager
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
    private val imageHandler: ImageHandler,
    private val networkUtils: NetworkUtils,  // Add this

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
                    imageHandler = imageHandler,
                    networkUtils = networkUtils  // Add this


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
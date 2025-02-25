package io.pawsomepals.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.facebook.FacebookSdk
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.ui.theme.PawsomePalsTheme
import io.pawsomepals.app.utils.CameraManager
import io.pawsomepals.app.viewmodel.AuthViewModel
import io.pawsomepals.app.viewmodel.ChatViewModel
import io.pawsomepals.app.viewmodel.DogProfileViewModel
import io.pawsomepals.app.viewmodel.HealthAdvisorViewModel
import io.pawsomepals.app.viewmodel.LocationPermissionViewModel
import io.pawsomepals.app.viewmodel.MatchesViewModel
import io.pawsomepals.app.viewmodel.NotificationViewModel
import io.pawsomepals.app.viewmodel.PhotoManagementViewModel
import io.pawsomepals.app.viewmodel.PlaydateViewModel
import io.pawsomepals.app.viewmodel.ProfileViewModel
import io.pawsomepals.app.viewmodel.QuestionnaireViewModel
import io.pawsomepals.app.viewmodel.RatingViewModel
import io.pawsomepals.app.viewmodel.SettingsViewModel
import io.pawsomepals.app.viewmodel.SwipingViewModel
import io.pawsomepals.app.viewmodel.TrainerTipsViewModel
import io.pawsomepals.app.viewmodel.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var isBackgrounded = false
    private var isAppClosing = false
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Inject
    lateinit var authStateManager: AuthStateManager  // Add this line

    @Inject
    lateinit var viewModelFactory: ViewModelFactory.Factory
    @Inject
    lateinit var locationService: LocationSearchService
    @Inject
    lateinit var authRepository: AuthRepository
    @Inject
    lateinit var cameraManager: CameraManager
    @Inject
    lateinit var dataManager: DataManager

    @Inject
    lateinit var firebaseAuth: FirebaseAuth  // Add this line




    private val dogProfileViewModel by viewModels<DogProfileViewModel>()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val playdateViewModel by viewModels<PlaydateViewModel>()
    private val chatViewModel by viewModels<ChatViewModel>()
    private val healthAdvisorViewModel by viewModels<HealthAdvisorViewModel>()
    private val settingsViewModel by viewModels<SettingsViewModel>()
    private val photoManagementViewModel by viewModels<PhotoManagementViewModel>()
    private val ratingViewModel by viewModels<RatingViewModel>()
    private val notificationViewModel by viewModels<NotificationViewModel>()
    private val swipingViewModel by viewModels<SwipingViewModel>()
    private val trainerTipsViewModel by viewModels<TrainerTipsViewModel>()
    private val questionnaireViewModel by viewModels<QuestionnaireViewModel>()
    private val locationPermissionViewModel by viewModels<LocationPermissionViewModel>()
    private val matchesViewModel by viewModels<MatchesViewModel>()
    private val authViewModel: AuthViewModel by viewModels()  // Add this line


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FacebookSdk.sdkInitialize(applicationContext)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataManager.startSync()
            }
        }


        setContent {
            PawsomePalsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        authViewModel = authViewModel,
                        profileViewModel = profileViewModel,
                        dogProfileViewModel = dogProfileViewModel,
                        playdateViewModel = playdateViewModel,
                        chatViewModel = chatViewModel,
                        healthAdvisorViewModel = healthAdvisorViewModel,
                        settingsViewModel = settingsViewModel,
                        photoManagementViewModel = photoManagementViewModel,
                        ratingViewModel = ratingViewModel,
                        notificationViewModel = notificationViewModel,
                        swipingViewModel = swipingViewModel,
                        trainerTipsViewModel = trainerTipsViewModel,
                        locationPermissionViewModel = locationPermissionViewModel,
                        questionnaireViewModel = questionnaireViewModel,
                        matchesViewModel = matchesViewModel,
                        locationService = locationService,
                        cameraManager = cameraManager,
                        dataManager = dataManager,
                        lifecycleScope = lifecycleScope,
                        authStateManager = authStateManager,  // Add this line
// Add this line

                    )
                }
            }
        }
    }

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            isAppClosing = true
            activityScope.launch {
                dataManager.cleanup()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel() // Clean up coroutines when activity is destroyed
    }
}
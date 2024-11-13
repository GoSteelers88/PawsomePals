package io.pawsomepals.app

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.pawsomepals.app.ui.theme.PawsomePalsTheme
import io.pawsomepals.app.utils.CameraPermissionManager
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity :  AppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelFactory.Factory

    private val authViewModel by viewModels<AuthViewModel>()
    private val profileViewModel by viewModels<ProfileViewModel> {
        viewModelFactory.create(this)
    }
    private val dogProfileViewModel by viewModels<DogProfileViewModel>()
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
    private val cameraPermissionManager by viewModels<CameraPermissionManager>()



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        questionnaireViewModel = questionnaireViewModel,
                        locationPermissionViewModel = locationPermissionViewModel,
                        matchesViewModel = matchesViewModel,
                        cameraPermissionManager = cameraPermissionManager

                    )
                }
            }
        }
    }
}
package com.example.pawsomepals

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.pawsomepals.ui.theme.PawsomePalsTheme
import com.example.pawsomepals.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint
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
    private val locationPermissionViewModel by viewModels<LocationPermissionViewModel>() // Added this line


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
                        locationPermissionViewModel = locationPermissionViewModel
                    )
                }
            }
        }
    }
}
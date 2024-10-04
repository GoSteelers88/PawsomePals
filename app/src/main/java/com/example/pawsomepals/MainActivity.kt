package com.example.pawsomepals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.pawsomepals.ui.theme.PawsomePalsTheme
import com.example.pawsomepals.viewmodel.AuthViewModel
import com.example.pawsomepals.viewmodel.ChatViewModel
import com.example.pawsomepals.viewmodel.DogProfileViewModel
import com.example.pawsomepals.viewmodel.HealthAdvisorViewModel
import com.example.pawsomepals.viewmodel.NotificationViewModel
import com.example.pawsomepals.viewmodel.PhotoManagementViewModel
import com.example.pawsomepals.viewmodel.PlaydateViewModel
import com.example.pawsomepals.viewmodel.ProfileViewModel
import com.example.pawsomepals.viewmodel.QuestionnaireViewModel
import com.example.pawsomepals.viewmodel.RatingViewModel
import com.example.pawsomepals.viewmodel.SettingsViewModel
import com.example.pawsomepals.viewmodel.SwipeViewModel
import com.example.pawsomepals.viewmodel.TrainerTipsViewModel
import com.example.pawsomepals.viewmodel.ViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val authViewModel by viewModels<AuthViewModel>()
    private val profileViewModel by viewModels<ProfileViewModel>()
    private val dogProfileViewModel by viewModels<DogProfileViewModel>()
    private val playdateViewModel by viewModels<PlaydateViewModel>()
    private val chatViewModel by viewModels<ChatViewModel>()
    private val healthAdvisorViewModel by viewModels<HealthAdvisorViewModel>()
    private val settingsViewModel by viewModels<SettingsViewModel>()
    private val photoManagementViewModel by viewModels<PhotoManagementViewModel>()
    private val ratingViewModel by viewModels<RatingViewModel>()
    private val notificationViewModel by viewModels<NotificationViewModel>()
    private val swipingViewModel by viewModels<SwipeViewModel>()
    private val trainerTipsViewModel by viewModels<TrainerTipsViewModel>()
    private val questionnaireViewModel: QuestionnaireViewModel by viewModels()

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
                        questionnaireViewModel = questionnaireViewModel
                    )
                }
            }
        }
    }
}
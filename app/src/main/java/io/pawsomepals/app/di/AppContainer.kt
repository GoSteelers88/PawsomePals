package io.pawsomepals.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.aallam.openai.client.OpenAI
import com.facebook.CallbackManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.R
import io.pawsomepals.app.ai.AIFeatures
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.AppDatabase
import io.pawsomepals.app.data.managers.CalendarManager
import io.pawsomepals.app.data.preferences.UserPreferences
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.OpenAIRepository
import io.pawsomepals.app.data.repository.PlaydateRepository
import io.pawsomepals.app.data.repository.QuestionRepository
import io.pawsomepals.app.data.repository.SettingsRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.discovery.ProfileDiscoveryService
import io.pawsomepals.app.discovery.queue.LocationAwareQueueManager
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.service.matching.MatchPreferences
import io.pawsomepals.app.utils.RecaptchaManager
import io.pawsomepals.app.utils.RemoteConfigManager
import io.pawsomepals.app.viewmodel.SwipingViewModel
import javax.inject.Inject


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")


class AppContainer @Inject constructor(
    private val context: Context,
    private val remoteConfigManager: RemoteConfigManager,
    private val recaptchaManager: RecaptchaManager,
    private val settingsRepository: SettingsRepository
) {
    init {
        FirebaseApp.initializeApp(context)
    }


    // Core Services - no dependencies
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }



    private val dataStore: DataStore<Preferences> by lazy { context.dataStore }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("PawsomePals", Context.MODE_PRIVATE)
    }

    private val userPreferences: UserPreferences by lazy {
        UserPreferences(context.dataStore)
    }

    // Basic Repositories - minimal dependencies
    val userRepository: UserRepository by lazy {
        UserRepository(
            userDao = database.userDao(),
            dogDao = database.dogDao(),
            firestore = firestore,
            auth = firebaseAuth
        )
    }

    // Auth Related Components
    val authStateManager: AuthStateManager by lazy {
        AuthStateManager(
            auth = firebaseAuth,
            userRepository = userRepository,
            firestore = firestore
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            auth = firebaseAuth,
            firestore = firestore,
            recaptchaManager = recaptchaManager,
            authStateManager = authStateManager,
            context = context
        )
    }


    val locationService: LocationService by lazy {
        LocationService(
            context = context,
            firestore = firestore,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        )
    }



    // Dependent Repositories
    val dogProfileRepository: DogProfileRepository by lazy {
        DogProfileRepository(
            userDao = database.userDao(),
            dogDao = database.dogDao(),
            firestore = firestore,
            auth = firebaseAuth,
            userRepository = userRepository
        )
    }

    // Calendar Related Components
    private val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager(
            context = context,
            firebaseAuth = firebaseAuth
        )
    }

    private val calendarManager: CalendarManager by lazy {
        CalendarManager(
            context = context,
            firestore = firestore,
            auth = firebaseAuth,
            playdateDao = database.playdateDao(),
            userPreferences = userPreferences
        )
    }

    val calendarService: CalendarService by lazy {
        CalendarService(
            context = context,
            calendarManager = calendarManager,
            googleAuthManager = googleAuthManager,
            firestore = firestore
        )
    }

    // Matching Related Components
    val matchPreferences: MatchPreferences by lazy {
        MatchPreferences()  // Use no-arg constructor since it's @Inject
    }

    val locationMatchingEngine: LocationMatchingEngine by lazy {
        LocationMatchingEngine(locationService)
    }

    val profileDiscoveryService: ProfileDiscoveryService by lazy {
        ProfileDiscoveryService(
            dogProfileRepository = dogProfileRepository,
            locationService = locationService,
            locationMatchingEngine = locationMatchingEngine,
            matchingService = matchingService,
            queueManager = locationAwareQueueManager
        )
    }

    private val locationAwareQueueManager: LocationAwareQueueManager by lazy {
        LocationAwareQueueManager(locationService)
    }

    val matchingService: MatchingService by lazy {
        MatchingService(
            locationService = locationService,
            locationMatchingEngine = locationMatchingEngine,
            matchPreferences = matchPreferences
        )
    }
    val matchRepository: MatchRepository by lazy {
        MatchRepository(
            firestore = firestore,
            realtimeDb = FirebaseDatabase.getInstance(),
            auth = firebaseAuth,
            matchingService = matchingService,
            dogProfileRepository = dogProfileRepository
        )
    }

    // Chat and Playdate
    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            firestore = firestore,
            chatDao = database.chatDao(),
            auth = firebaseAuth,
            dogProfileRepository = dogProfileRepository,
            matchRepository = matchRepository
        )
    }

    val notificationManager: NotificationManager by lazy {
        NotificationManager(context)
    }

    val playdateRepository: PlaydateRepository by lazy {
        PlaydateRepository(
            database.playdateDao(),
            userRepository,
            calendarService,
            firestore,
            notificationManager
        )
    }

    // AI Related Components
    val openAI: OpenAI by lazy {
        OpenAI(token = remoteConfigManager.getOpenAIKey())
    }

    val openAIRepository: OpenAIRepository by lazy {
        OpenAIRepository(openAI)
    }

    val questionRepository: QuestionRepository by lazy {
        QuestionRepository(database.questionDao(), firestore)
    }

    val aiFeatures: AIFeatures by lazy {
        AIFeatures(openAI, userRepository, questionRepository)
    }

    // Additional Services
    val locationSuggestionService: LocationSuggestionService by lazy {
        LocationSuggestionService(
            context = context,
            placesClient = Places.createClient(context),  // Add this
            firestore = firestore
        )
    }

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val facebookCallbackManager: CallbackManager by lazy {
        CallbackManager.Factory.create()
    }

    // ViewModels - must be initialized last
    val swipingViewModel: SwipingViewModel by lazy {
        SwipingViewModel(
            matchRepository = matchRepository,
            dogProfileRepository = dogProfileRepository,
            matchingService = matchingService,
            settingsRepository = settingsRepository,
            locationService = locationService,
            chatRepository = chatRepository,
            userRepository = userRepository,
            authStateManager = authStateManager,
            authRepository = authRepository,
            auth = firebaseAuth,
            profileDiscoveryService = profileDiscoveryService,
            locationMatchingEngine = locationMatchingEngine,
            locationAwareQueueManager = locationAwareQueueManager,

        )
    }
}
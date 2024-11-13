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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.R
import io.pawsomepals.app.ai.AIFeatures
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
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.utils.RecaptchaManager
import io.pawsomepals.app.utils.RemoteConfigManager
import javax.inject.Inject


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppContainer @Inject constructor(
    private val context: Context,
    private val remoteConfigManager: RemoteConfigManager,
    private val recaptchaManager: RecaptchaManager


) {
    init {
        FirebaseApp.initializeApp(context)
    }



    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("PawsomePals", Context.MODE_PRIVATE)
    }

    private val userPreferences: UserPreferences by lazy {
        UserPreferences(context.dataStore)
    }


    val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }

    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // Add required dependencies for Calendar functionality
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
            userPreferences = userPreferences  // Changed to use userPreferences
        )
    }

    val calendarService: CalendarService by lazy {
        CalendarService(
            context = context,
            calendarManager = calendarManager,
            googleAuthManager = googleAuthManager,
            firestore = firestore  // Added this parameter
        )
    }

    val userRepository: UserRepository by lazy {
        UserRepository(
            database.userDao(),
            database.dogDao(),
            firestore,
            firebaseAuth
        )
    }
    val dogProfileRepository: DogProfileRepository by lazy {
        DogProfileRepository(firestore, userRepository)
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

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            firestore = firestore,
            chatDao = database.chatDao(),  // Add this
            auth = firebaseAuth,
            dogProfileRepository = dogProfileRepository,
            matchRepository = matchRepository
        )
    }

    val playdateRepository: PlaydateRepository by lazy {
        PlaydateRepository(
            database.playdateDao(),
            userRepository,
            calendarService,
            firestore,
            notificationManager  // Add this parameter
        )
    }
    // Rest of the code remains the same
    val openAI: OpenAI by lazy {
        OpenAI(token = remoteConfigManager.getOpenAIKey())
    }

    val openAIRepository: OpenAIRepository by lazy { OpenAIRepository(openAI) }

    val locationService: LocationService by lazy {
        LocationService(
            context = context,
            firestore = firestore,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        )
    }
    val matchPreferences: MatchingService.MatchPreferences by lazy {
        MatchingService.MatchPreferences(
            maxDistance = 50.0,
            minCompatibilityScore = 0.4,
            prioritizeEnergy = false,
            prioritizeAge = false,
            prioritizeBreed = false
        )
    }

    val matchingService: MatchingService by lazy {
        MatchingService(locationService, matchPreferences)
    }

    val notificationManager: NotificationManager by lazy { NotificationManager(context) }

    val locationSuggestionService: LocationSuggestionService by lazy {
        LocationSuggestionService(context,firestore)
    }


    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val facebookCallbackManager: CallbackManager by lazy { CallbackManager.Factory.create() }

    val authRepository: AuthRepository by lazy {
        AuthRepository(firebaseAuth, firestore, recaptchaManager, context)
    }

    val questionRepository: QuestionRepository by lazy {
        QuestionRepository(database.questionDao(), firestore)
    }

    val aiFeatures: AIFeatures by lazy {
        AIFeatures(openAI, userRepository, questionRepository)
    }
}
package com.example.pawsomepals.di

import android.content.Context
import com.aallam.openai.client.OpenAI
import com.example.pawsomepals.BuildConfig
import com.example.pawsomepals.R
import com.example.pawsomepals.ai.AIFeatures
import com.example.pawsomepals.data.AppDatabase
import com.example.pawsomepals.data.repository.*
import com.example.pawsomepals.notification.NotificationManager
import com.example.pawsomepals.service.LocationService
import com.example.pawsomepals.service.LocationSuggestionService
import com.example.pawsomepals.service.MatchingService
import com.example.pawsomepals.utils.RecaptchaManager
import com.facebook.CallbackManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(private val context: Context) {
    init {
        FirebaseApp.initializeApp(context)
    }

    val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }

    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val userRepository: UserRepository by lazy {
        UserRepository(database.userDao(), database.dogDao(), firestore)
    }

    val chatRepository: ChatRepository by lazy { ChatRepository(firestore) }

    val playdateRepository: PlaydateRepository by lazy {
        PlaydateRepository(database.playdateDao(), userRepository)
    }

    val openAI: OpenAI by lazy {
        OpenAI(token = BuildConfig.OPENAI_API_KEY)
    }

    val openAIRepository: OpenAIRepository by lazy { OpenAIRepository(openAI) }

    val locationService: LocationService by lazy {
        LocationService(context, LocationServices.getFusedLocationProviderClient(context))
    }

    val matchingService: MatchingService by lazy { MatchingService(locationService) }

    val notificationManager: NotificationManager by lazy { NotificationManager(context) }

    val locationSuggestionService: LocationSuggestionService by lazy {
        LocationSuggestionService(context)
    }

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val facebookCallbackManager: CallbackManager by lazy { CallbackManager.Factory.create() }

    val recaptchaManager: RecaptchaManager by lazy { RecaptchaManager(context) }

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
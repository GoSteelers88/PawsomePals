package com.example.pawsomepals.di

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.savedstate.SavedStateRegistryOwner
import com.aallam.openai.client.OpenAI
import com.example.pawsomepals.BuildConfig
import com.example.pawsomepals.R
import com.example.pawsomepals.ai.AIFeatures
import com.example.pawsomepals.auth.GoogleAuthManager
import com.example.pawsomepals.data.AppDatabase
import com.example.pawsomepals.data.DataManager
import com.example.pawsomepals.data.dao.PhotoDao
import com.example.pawsomepals.data.dao.SettingsDao
import com.example.pawsomepals.data.remote.PhotoApi
import com.example.pawsomepals.data.remote.SettingsApi
import com.example.pawsomepals.data.repository.AuthRepository
import com.example.pawsomepals.data.repository.ChatRepository
import com.example.pawsomepals.data.repository.DogProfileRepository
import com.example.pawsomepals.data.repository.NotificationRepository
import com.example.pawsomepals.data.repository.OpenAIRepository
import com.example.pawsomepals.data.repository.PhotoRepository
import com.example.pawsomepals.data.repository.PlaydateRepository
import com.example.pawsomepals.data.repository.QuestionRepository
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.notification.NotificationManager
import com.example.pawsomepals.service.LocationService
import com.example.pawsomepals.service.LocationSuggestionService
import com.example.pawsomepals.service.MatchingService
import com.example.pawsomepals.subscription.SubscriptionManager
import com.example.pawsomepals.utils.ImageHandler
import com.example.pawsomepals.utils.NetworkUtils
import com.example.pawsomepals.utils.RecaptchaManager
import com.example.pawsomepals.viewmodel.ViewModelFactory
import com.facebook.CallbackManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {


    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance()
    }


    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        recaptchaManager: RecaptchaManager,
        @ApplicationContext context: Context
    ): AuthRepository {
        return AuthRepository(firebaseAuth, firestore, recaptchaManager, context)
    }

    @Provides
    @Singleton
    fun provideSubscriptionManager(userRepository: UserRepository): SubscriptionManager {
        return SubscriptionManager(userRepository)
    }

    @Provides
    @Singleton
    fun provideRecaptchaManager(@ApplicationContext context: Context): RecaptchaManager {
        return RecaptchaManager(context)
    }


    @Provides
    @Singleton
    fun provideNotificationRepository(firestore: FirebaseFirestore, auth: FirebaseAuth): NotificationRepository {
        return NotificationRepository(firestore, auth)
    }

    @Provides
    @Singleton
    fun provideSettingsDao(appDatabase: AppDatabase): SettingsDao {
        return appDatabase.settingsDao()
    }





    @Provides
    @Singleton
    fun provideSettingsApi(retrofit: Retrofit): SettingsApi {
        return retrofit.create(SettingsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/") // Replace with your actual API base URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePhotoApi(retrofit: Retrofit): PhotoApi {
        return retrofit.create(PhotoApi::class.java)
    }

    @Provides
    @Singleton
    fun providePhotoDao(appDatabase: AppDatabase): PhotoDao {
        return appDatabase.photoDao()
    }

    @Provides
    @Singleton
    fun providePhotoRepository(photoApi: PhotoApi, photoDao: PhotoDao): PhotoRepository {
        return PhotoRepository(photoApi, photoDao)
    }

    @Provides
    @Singleton
    fun provideQuestionRepository(db: AppDatabase, firestore: FirebaseFirestore): QuestionRepository {
        return QuestionRepository(db.questionDao(), firestore)
    }

    @Provides
    @Singleton
    fun provideUserRepository(db: AppDatabase, firestore: FirebaseFirestore): UserRepository {
        return UserRepository(db.userDao(), db.dogDao(), firestore)
    }

    fun provideDogProfileRepository(
        firestore: FirebaseFirestore,
        userRepository: UserRepository
    ): DogProfileRepository {
        return DogProfileRepository(firestore, userRepository)
    }

    @Provides
    @Singleton
    fun provideChatRepository(firestore: FirebaseFirestore): ChatRepository {
        return ChatRepository(firestore)
    }

    @Provides
    @Singleton
    fun providePlaydateRepository(db: AppDatabase, userRepository: UserRepository): PlaydateRepository {
        return PlaydateRepository(db.playdateDao(), userRepository)
    }

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }

    @Provides
    @Singleton
    fun provideLocationService(@ApplicationContext context: Context, fusedLocationClient: FusedLocationProviderClient): LocationService {
        return LocationService(context, fusedLocationClient)
    }

    @Provides
    @Singleton
    fun provideMatchingService(locationService: LocationService): MatchingService {
        return MatchingService(locationService)
    }

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager {
        return NotificationManager(context)
    }

    @Provides
    @Singleton
    fun provideLocationSuggestionService(@ApplicationContext context: Context): LocationSuggestionService {
        return LocationSuggestionService(context)
    }

    @Provides
    @Singleton
    fun provideOpenAI(): OpenAI {
        return OpenAI(token = BuildConfig.OPENAI_API_KEY)
    }

    @Provides
    @Singleton
    fun provideOpenAIRepository(openAI: OpenAI): OpenAIRepository {
        return OpenAIRepository(openAI)
    }


    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

    @Provides
    @Singleton
    fun provideDataManager(
        appDatabase: AppDatabase,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        storage: FirebaseStorage,
        @ApplicationContext context: Context,
        questionRepository: QuestionRepository,
        imageHandler: ImageHandler  // Added this parameter
    ): DataManager {
        return DataManager(
            appDatabase = appDatabase,
            firestore = firestore,
            auth = auth,
            storage = storage,
            context = context,
            questionRepository = questionRepository,
            imageHandler = imageHandler  // Added this parameter
        )
    }
    @Provides
    @Singleton
    fun provideNetworkUtils(@ApplicationContext context: Context): NetworkUtils {
        return NetworkUtils(context)
    }


    @Provides
    @Singleton
    fun provideImageHandler(@ApplicationContext context: Context): ImageHandler {
        return ImageHandler(context)
    }

    @Provides
    fun provideSavedStateRegistryOwner(
        @ApplicationContext context: Context
    ): SavedStateRegistryOwner {
        return context as ComponentActivity
    }


@Provides
@Singleton
fun provideGoogleSignInClient(@ApplicationContext context: Context): GoogleSignInClient {
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    return GoogleSignIn.getClient(context, gso)
}

@Provides
@Singleton
fun provideGoogleAuthManager(
    @ApplicationContext context: Context,
    firebaseAuth: FirebaseAuth
): GoogleAuthManager = GoogleAuthManager(context, firebaseAuth)


@Provides
@Singleton
fun provideCallbackManager(): CallbackManager {
    return CallbackManager.Factory.create()
}


@Provides
@Singleton
fun provideFirebaseFirestore(): FirebaseFirestore {
    return FirebaseFirestore.getInstance()
}

@Provides
@Singleton
fun provideAIFeatures(
    openAI: OpenAI,
    userRepository: UserRepository,
    questionRepository: QuestionRepository
): AIFeatures {
    return AIFeatures(openAI, userRepository, questionRepository)
}}


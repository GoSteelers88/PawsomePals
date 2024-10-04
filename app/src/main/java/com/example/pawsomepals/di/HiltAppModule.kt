package com.example.pawsomepals.di

import android.content.Context
import com.aallam.openai.client.OpenAI
import com.example.pawsomepals.BuildConfig
import com.example.pawsomepals.R
import com.example.pawsomepals.ai.AIFeatures
import com.example.pawsomepals.data.AppDatabase
import com.example.pawsomepals.data.dao.PhotoDao
import com.example.pawsomepals.data.dao.SettingsDao
import com.example.pawsomepals.data.remote.PhotoApi
import com.example.pawsomepals.data.remote.SettingsApi
import com.example.pawsomepals.data.repository.AuthRepository
import com.example.pawsomepals.data.repository.ChatRepository
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
import com.example.pawsomepals.utils.RecaptchaManager
import com.facebook.CallbackManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        recaptchaManager: RecaptchaManager,
        @ApplicationContext context: Context
    ): AuthRepository {
        return AuthRepository(firebaseAuth, recaptchaManager, context)
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
    fun provideNotificationRepository(firestore: FirebaseFirestore): NotificationRepository {
        return NotificationRepository(firestore)
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
    fun provideQuestionRepository(db: AppDatabase, firebaseRef: DatabaseReference): QuestionRepository {
        return QuestionRepository(db.questionDao(), firebaseRef)
    }

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseDatabaseReference(firebaseDatabase: FirebaseDatabase): DatabaseReference {
        return firebaseDatabase.reference
    }

    @Provides
    @Singleton
    fun provideUserRepository(db: AppDatabase, firebaseDatabase: FirebaseDatabase): UserRepository {
        return UserRepository(db.userDao(), db.dogDao(), firebaseDatabase.reference)
    }

    @Provides
    @Singleton
    fun provideChatRepository(firebaseDatabase: FirebaseDatabase): ChatRepository {
        return ChatRepository(firebaseDatabase)
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
    fun provideGoogleSignInClient(@ApplicationContext context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

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
    }
}
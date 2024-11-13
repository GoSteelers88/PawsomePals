package io.pawsomepals.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.aallam.openai.client.OpenAI
import com.facebook.CallbackManager
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.pawsomepals.app.ai.AIFeatures
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.AppDatabase
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.dao.ChatDao
import io.pawsomepals.app.data.dao.DogFriendlyLocationDao
import io.pawsomepals.app.data.dao.PhotoDao
import io.pawsomepals.app.data.dao.PlaydateDao
import io.pawsomepals.app.data.dao.QuestionDao
import io.pawsomepals.app.data.dao.SettingsDao
import io.pawsomepals.app.data.dao.TimeSlotDao
import io.pawsomepals.app.data.managers.CalendarManager
import io.pawsomepals.app.data.preferences.UserPreferences
import io.pawsomepals.app.data.remote.PhotoApi
import io.pawsomepals.app.data.remote.SettingsApi
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.ITimeSlotRepository
import io.pawsomepals.app.data.repository.LocationRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.NotificationRepository
import io.pawsomepals.app.data.repository.OpenAIRepository
import io.pawsomepals.app.data.repository.PhotoRepository
import io.pawsomepals.app.data.repository.PlaydateRepository
import io.pawsomepals.app.data.repository.QuestionRepository
import io.pawsomepals.app.data.repository.TimeSlotRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.AchievementService
import io.pawsomepals.app.service.AnalyticsService
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.EnhancedLocationService
import io.pawsomepals.app.service.location.GoogleMapsService
import io.pawsomepals.app.service.location.LocationCache
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.service.location.PlaceService
import io.pawsomepals.app.utils.CameraUtils
import io.pawsomepals.app.utils.ImageHandler
import io.pawsomepals.app.utils.LocationMapper
import io.pawsomepals.app.utils.LocationValidator
import io.pawsomepals.app.utils.NetworkUtils
import io.pawsomepals.app.utils.RecaptchaManager
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.CoroutineDispatcher
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Core Firebase Providers
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    // Database Providers
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideSettingsDao(appDatabase: AppDatabase): SettingsDao = appDatabase.settingsDao()

    @Provides
    @Singleton
    fun providePhotoDao(appDatabase: AppDatabase): PhotoDao = appDatabase.photoDao()

    @Provides
    @Singleton
    fun providePlaydateDao(appDatabase: AppDatabase): PlaydateDao = appDatabase.playdateDao()

    @Provides
    @Singleton
    fun provideDogFriendlyLocationDao(appDatabase: AppDatabase): DogFriendlyLocationDao =
        appDatabase.dogFriendlyLocationDao()

    @Provides
    @Singleton
    fun provideAchievementDao(appDatabase: AppDatabase) = appDatabase.achievementDao()

    // Network & API Providers
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideSettingsApi(retrofit: Retrofit): SettingsApi =
        retrofit.create(SettingsApi::class.java)

    @Provides
    @Singleton
    fun providePhotoApi(retrofit: Retrofit): PhotoApi = retrofit.create(PhotoApi::class.java)

    // Config & Utils Providers
    @Provides
    @Singleton
    fun provideRemoteConfigManager(): RemoteConfigManager {
        val remoteConfigManager = RemoteConfigManager()
        remoteConfigManager.initialize()
        return remoteConfigManager
    }

    @Provides
    @Singleton
    fun provideRecaptchaManager(
        @ApplicationContext context: Context,
        remoteConfigManager: RemoteConfigManager
    ): RecaptchaManager = RecaptchaManager(context, remoteConfigManager)

    @Provides
    @Singleton
    fun provideNetworkUtils(@ApplicationContext context: Context): NetworkUtils =
        NetworkUtils(context)

    @Provides
    @Singleton
    fun provideImageHandler(@ApplicationContext context: Context): ImageHandler =
        ImageHandler(context)

    // Repository Providers
    @Provides
    @Singleton
    fun provideUserRepository(
        db: AppDatabase,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): UserRepository = UserRepository(db.userDao(), db.dogDao(), firestore, auth)

    @Provides
    @Singleton
    fun provideEnhancedLocationService(
        googleMapsService: GoogleMapsService,
        placeService: PlaceService,
        locationCache: LocationCache
    ): EnhancedLocationService = EnhancedLocationService(
        googleMapsService = googleMapsService,
        placeService = placeService,
        locationCache = locationCache
    )


    @Provides
    @Singleton
    fun provideQuestionDao(appDatabase: AppDatabase) = appDatabase.questionDao()

    @Provides
    @Singleton
    fun provideQuestionRepository(
        questionDao: QuestionDao,
        firestore: FirebaseFirestore
    ): QuestionRepository = QuestionRepository(questionDao, firestore)

    @Provides
    @Singleton
    fun providePhotoRepository(
        photoApi: PhotoApi,
        photoDao: PhotoDao,
        storage: FirebaseStorage
    ): PhotoRepository = PhotoRepository(photoApi, photoDao, storage)

    @Provides
    @Singleton
    fun providePlaydateRepository(
        db: AppDatabase,
        userRepository: UserRepository,
        calendarService: CalendarService,
        firestore: FirebaseFirestore,
        notificationManager: NotificationManager  // Add this parameter
    ): PlaydateRepository = PlaydateRepository(
        db.playdateDao(),
        userRepository,
        calendarService,
        firestore,
        notificationManager  // Pass it to constructor
    )

    @Provides
    @Singleton
    fun provideLocationRepository(
        dogFriendlyLocationDao: DogFriendlyLocationDao,
        locationSearchService: LocationSearchService,
        locationMapper: LocationMapper,
        locationValidator: LocationValidator,
        firestore: FirebaseFirestore
    ): LocationRepository = LocationRepository(
        dogFriendlyLocationDao,
        locationSearchService,
        locationMapper,
        locationValidator,
        firestore
    )

    @Provides
    @Singleton
    fun provideChatRepository(
        firestore: FirebaseFirestore,
        chatDao: ChatDao,  // Add this
        auth: FirebaseAuth,
        dogProfileRepository: DogProfileRepository,
        matchRepository: MatchRepository
    ): ChatRepository = ChatRepository(
        firestore = firestore,
        chatDao = chatDao,
        auth = auth,
        dogProfileRepository = dogProfileRepository,
        matchRepository = matchRepository
    )
    @Provides
    @Singleton
    fun provideChatDao(appDatabase: AppDatabase): ChatDao = appDatabase.chatDao()

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        recaptchaManager: RecaptchaManager,
        @ApplicationContext context: Context
    ): AuthRepository = AuthRepository(firebaseAuth, firestore, recaptchaManager, context)

    @Provides
    @Singleton
    fun provideNotificationRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): NotificationRepository = NotificationRepository(firestore, auth)

    // Service Providers
    @Provides
    @Singleton
    fun provideMatchPreferences(): MatchingService.MatchPreferences =
        MatchingService.MatchPreferences(
            maxDistance = 50.0,
            minCompatibilityScore = 0.4,
            prioritizeEnergy = false,
            prioritizeAge = false,
            prioritizeBreed = false
        )
    @Provides
    @Singleton
    fun provideMatchRepository(
        firestore: FirebaseFirestore,
        realtimeDb: FirebaseDatabase,  // You already have this provider
        auth: FirebaseAuth,
        matchingService: MatchingService,
        dogProfileRepository: DogProfileRepository
    ): MatchRepository = MatchRepository(
        firestore = firestore,
        realtimeDb = realtimeDb,
        auth = auth,
        matchingService = matchingService,
        dogProfileRepository = dogProfileRepository
    )



    @Provides
    @Singleton
    fun provideMatchingService(
        locationService: LocationService,
        matchPreferences: MatchingService.MatchPreferences
    ): MatchingService = MatchingService(locationService, matchPreferences)

    @Provides
    @Singleton
    fun provideDogProfileRepository(
        firestore: FirebaseFirestore,
        userRepository: UserRepository
    ): DogProfileRepository = DogProfileRepository(firestore, userRepository)

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)


    @Provides
    @Singleton
    fun provideLocationService(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        fusedLocationClient: FusedLocationProviderClient
    ): LocationService = LocationService(
        context = context,
        firestore = firestore,
        fusedLocationClient = fusedLocationClient
    )

    @Provides
    @Singleton
    fun provideLocationSuggestionService(
        @ApplicationContext context: Context, firestore: FirebaseFirestore

    ): LocationSuggestionService = LocationSuggestionService(context, firestore)

    @Provides
    @Singleton
    fun provideLocationSearchService(
        placesClient: PlacesClient,
        remoteConfigManager: RemoteConfigManager
    ): LocationSearchService = LocationSearchService(
        placesClient = placesClient,
        remoteConfigManager = remoteConfigManager
    )

    @Provides
    @Singleton
    fun provideGoogleMapsService(
        @ApplicationContext context: Context
    ): GoogleMapsService = GoogleMapsService(context)

    @Provides
    @Singleton
    fun provideLocationMapper(): LocationMapper = LocationMapper()

    @Provides
    @Singleton
    fun provideLocationValidator(): LocationValidator = LocationValidator()

    // Calendar Providers
    @Provides
    @Singleton
    fun provideCalendarManager(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        playdateDao: PlaydateDao,
        userPreferences: UserPreferences
    ): CalendarManager = CalendarManager(context, firestore, auth, playdateDao, userPreferences)

    @Provides
    @Singleton
    fun provideTimeSlotDao(appDatabase: AppDatabase): TimeSlotDao = appDatabase.timeSlotDao()


    @Provides
    @Singleton
    fun provideTimeSlotRepository(
        timeSlotDao: TimeSlotDao,
        @IoDispatcher dispatcher: CoroutineDispatcher
    ): ITimeSlotRepository {
        return TimeSlotRepository(timeSlotDao, dispatcher)
    }


    @Provides
    @Singleton
    fun provideCalendarService(
        @ApplicationContext context: Context,
        calendarManager: CalendarManager,
        googleAuthManager: GoogleAuthManager,
        firestore: FirebaseFirestore
    ): CalendarService = CalendarService(context, calendarManager, googleAuthManager, firestore)

    // Auth Providers
    @Provides
    @Singleton
    fun provideSignInClient(
        @ApplicationContext context: Context
    ): SignInClient = Identity.getSignInClient(context)

    @Provides
    @Singleton
    fun provideGoogleAuthManager(
        @ApplicationContext context: Context,
        firebaseAuth: FirebaseAuth
    ): GoogleAuthManager = GoogleAuthManager(context, firebaseAuth)

    @Provides
    @Singleton
    fun provideCallbackManager(): CallbackManager = CallbackManager.Factory.create()

    // AI Providers
    @Provides
    @Singleton
    fun provideOpenAI(remoteConfigManager: RemoteConfigManager): OpenAI {
        val apiKey = remoteConfigManager.getOpenAIKey()
            ?: throw IllegalStateException("OpenAI API key not found")
        return OpenAI(token = apiKey)
    }

    @Provides
    @Singleton
    fun provideAIFeatures(
        openAI: OpenAI,
        userRepository: UserRepository,
        questionRepository: QuestionRepository
    ): AIFeatures = AIFeatures(openAI, userRepository, questionRepository)

    @Provides
    @Singleton
    fun provideOpenAIRepository(openAI: OpenAI): OpenAIRepository = OpenAIRepository(openAI)

    // Other Providers
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            produceFile = { context.preferencesDataStoreFile("user_preferences") }
        )

    @Provides
    @Singleton
    fun provideUserPreferences(dataStore: DataStore<Preferences>): UserPreferences =
        UserPreferences(dataStore)

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideCameraUtils(@ApplicationContext context: Context): CameraUtils =
        CameraUtils(context)

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager = NotificationManager(context)

    @Provides
    @Singleton
    fun provideAnalyticsService(): AnalyticsService = AnalyticsService()

    @Provides
    @Singleton
    fun provideAchievementService(
        dataManager: DataManager,
        firestore: FirebaseFirestore
    ): AchievementService = AchievementService(dataManager, firestore)












}


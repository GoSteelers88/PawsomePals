package io.pawsomepals.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.pawsomepals.app.ai.AIFeatures
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.auth.AuthenticationDelegate
import io.pawsomepals.app.auth.AuthenticationDelegateImpl
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.AppDatabase
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.dao.ChatDao
import io.pawsomepals.app.data.dao.DogDao
import io.pawsomepals.app.data.dao.DogFriendlyLocationDao
import io.pawsomepals.app.data.dao.MatchDao
import io.pawsomepals.app.data.dao.PhotoDao
import io.pawsomepals.app.data.dao.PlaydateDao
import io.pawsomepals.app.data.dao.QuestionDao
import io.pawsomepals.app.data.dao.SettingsDao
import io.pawsomepals.app.data.dao.TimeSlotDao
import io.pawsomepals.app.data.dao.UserDao
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
import io.pawsomepals.app.discovery.ProfileDiscoveryService
import io.pawsomepals.app.discovery.queue.LocationAwareQueueManager
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.AchievementService
import io.pawsomepals.app.service.AnalyticsService
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.UserServiceManager
import io.pawsomepals.app.service.location.EnhancedLocationService
import io.pawsomepals.app.service.location.GoogleMapsService
import io.pawsomepals.app.service.location.LocationCache
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationProvider
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.service.location.PlaceService
import io.pawsomepals.app.service.location.PlacesInitializer
import io.pawsomepals.app.service.weather.OpenWeatherApi
import io.pawsomepals.app.service.weather.WeatherService
import io.pawsomepals.app.utils.CameraManager
import io.pawsomepals.app.utils.CameraUtils
import io.pawsomepals.app.utils.LocationMapper
import io.pawsomepals.app.utils.LocationValidator
import io.pawsomepals.app.utils.NetworkUtils
import io.pawsomepals.app.utils.RecaptchaManager
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.Executor
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Core Firebase Providers

    @Provides
    @Singleton
    fun provideAuthStateManager(
        auth: FirebaseAuth,
        userRepository: UserRepository,
        firestore: FirebaseFirestore
    ): AuthStateManager = AuthStateManager(auth, userRepository, firestore)


    @Provides
    @Singleton
    fun provideLazyDataManager(provider: Provider<DataManager>): Lazy<DataManager> {
        return lazy { provider.get() }
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()


    @Provides
    @Singleton
    fun provideProfileDiscoveryService(
        dogProfileRepository: DogProfileRepository,
        locationService: LocationService,
        locationMatchingEngine: LocationMatchingEngine,
        matchingService: MatchingService,
        queueManager: LocationAwareQueueManager
    ): ProfileDiscoveryService = ProfileDiscoveryService(
        dogProfileRepository,
        locationService,
        locationMatchingEngine,
        matchingService,
        queueManager
    )

    @Provides
    @Singleton
    fun provideLocationAwareQueueManager(
        locationService: LocationService
    ): LocationAwareQueueManager = LocationAwareQueueManager(locationService)

    @Provides
    @Singleton
    fun provideLocationMatchingEngine(
        locationService: LocationService
    ): LocationMatchingEngine = LocationMatchingEngine(locationService)

    @Provides
    @Singleton
    fun provideCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()



    @Provides
    @Singleton
    fun provideDataManager(
        appDatabase: AppDatabase,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        storage: FirebaseStorage,
        @ApplicationContext context: Context,
        locationService: LocationService,
        matchingService: MatchingService,
        calendarService: CalendarService,
        googleAuthManager: GoogleAuthManager,
        authStateManager: AuthStateManager,
        photoRepository: PhotoRepository
    ): DataManager = DataManager(
        appDatabase = appDatabase,
        firestore = firestore,
        auth = auth,
        storage = storage,
        context = context,
        locationService = locationService,
        matchingService = matchingService,
        calendarService = calendarService,
        googleAuthManager = googleAuthManager,
        authStateManager = authStateManager,
        photoRepository = photoRepository
    )




    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideAuthenticationDelegate(
        dataManager: Lazy<DataManager>
    ): AuthenticationDelegate = AuthenticationDelegateImpl(dataManager)




    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class DataManagerQualifier


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
    fun provideRemoteConfigManager(): RemoteConfigManager = RemoteConfigManager()

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



    // Repository Providers
    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        dogDao: DogDao,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): UserRepository = UserRepository(
        userDao = userDao,
        dogDao = dogDao,
        firestore = firestore,
        auth = auth
    )
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
    fun provideUserServiceManager(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        userDao: UserDao,
        dogDao: DogDao,
        matchDao: MatchDao,
        @ApplicationScope scope: CoroutineScope
    ): UserServiceManager {
        return UserServiceManager(
            auth = auth,
            firestore = firestore,
            userDao = userDao,
            dogDao = dogDao,
            matchDao = matchDao,
            scope = scope
        )
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): UserDao = appDatabase.userDao()

    @Provides
    @Singleton
    fun provideDogDao(appDatabase: AppDatabase): DogDao = appDatabase.dogDao()

    @Provides
    @Singleton
    fun provideMatchDao(appDatabase: AppDatabase): MatchDao = appDatabase.matchDao()
    @Provides
    @Singleton
    fun provideCameraManager(
        @ApplicationContext context: Context,
        mainExecutor: Executor
    ): CameraManager {
        return CameraManager(context, mainExecutor)
    }
    @Provides
    @Singleton
    fun provideMainExecutor(@ApplicationContext context: Context): Executor {
        return ContextCompat.getMainExecutor(context)
    }


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
        storage: FirebaseStorage,
        @ApplicationContext context: Context
    ): PhotoRepository = PhotoRepository(
        photoApi = photoApi,
        photoDao = photoDao,
        storage = storage,
        context = context
    )
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
        authStateManager: AuthStateManager,
        @ApplicationContext context: Context
    ): AuthRepository = AuthRepository(
        auth = firebaseAuth,
        firestore = firestore,
        recaptchaManager = recaptchaManager,
        authStateManager = authStateManager,
        context = context
    )

    @Provides
    @Singleton
    fun provideNotificationRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): NotificationRepository = NotificationRepository(firestore, auth)

    // Service Providers

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
        locationMatchingEngine: LocationMatchingEngine,
        matchPreferences: io.pawsomepals.app.service.matching.MatchPreferences // Update import
    ): MatchingService = MatchingService(
        locationService,
        locationMatchingEngine,
        matchPreferences
    )

    @Provides
    @Singleton
    fun provideDogProfileRepository(
        userDao: UserDao,
        dogDao: DogDao,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        userRepository: UserRepository
    ): DogProfileRepository = DogProfileRepository(
        userDao = userDao,
        dogDao = dogDao,
        firestore = firestore,
        auth = auth,
        userRepository = userRepository
    )
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
        fusedLocationClient: FusedLocationProviderClient,
    ): LocationService = LocationService(
        context = context,
        firestore = firestore,
        fusedLocationClient = fusedLocationClient,
    )


    @Provides
    @Singleton
    fun provideLocationProvider(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        fusedLocationClient: FusedLocationProviderClient
    ): LocationProvider {
        return LocationService(context, firestore, fusedLocationClient)
    }


    @Provides
    @Singleton
    fun provideLocationSuggestionService(
        @ApplicationContext context: Context,
        placesClient: PlacesClient,  // Changed from firestore
        firestore: FirebaseFirestore
    ): LocationSuggestionService = LocationSuggestionService(
        context = context,
        placesClient = placesClient,  // Pass placesClient
        firestore = firestore
    )
    @Provides
    @Singleton
    fun provideLocationSearchService(
        @ApplicationContext context: Context,
        remoteConfigManager: RemoteConfigManager,
        placesInitializer: PlacesInitializer,
        locationProvider: LocationProvider
    ): LocationSearchService {
        return LocationSearchService(
            context = context,
            placesInitializer = placesInitializer,
            locationProvider = locationProvider,
            remoteConfigManager = remoteConfigManager
        )
    }



    @Provides
    @Singleton
    fun provideOpenWeatherApi(): OpenWeatherApi {
        return Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWeatherService(
        api: OpenWeatherApi,
        remoteConfig: RemoteConfigManager
    ): WeatherService = WeatherService(api, remoteConfig)
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


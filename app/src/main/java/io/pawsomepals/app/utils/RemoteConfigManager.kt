package io.pawsomepals.app.utils

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import io.pawsomepals.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

@Singleton
class RemoteConfigManager @Inject constructor() {
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
    private var isInitialized = false

    companion object {
        private const val TAG = "RemoteConfigManager"
        private const val DEFAULT_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_FETCH_INTERVAL = 3600L // 1 hour
        private const val DEBUG_FETCH_INTERVAL = 0L

        // Config Keys - these should match exactly with Firebase Remote Config parameter names
        const val KEY_MAPS = "maps_api_key"
        const val KEY_GOOGLE_SIGN_IN = "google_sign_in_key"
        const val KEY_RECAPTCHA = "recaptcha_key"
        const val KEY_FACEBOOK_APP = "facebook_app_id"
        const val KEY_FACEBOOK_TOKEN = "facebook_token"
        const val KEY_OPENAI = "openai_key"
        const val KEY_WEATHER = "weather_key"

        // Default Values - Consider moving to separate sealed class/object
        private val DEFAULT_CONFIG = mapOf(
            KEY_MAPS to BuildConfig.DEFAULT_MAPS_API_KEY,
            KEY_GOOGLE_SIGN_IN to BuildConfig.DEFAULT_GOOGLE_SIGN_IN_KEY,
            KEY_RECAPTCHA to BuildConfig.DEFAULT_RECAPTCHA_KEY,
            KEY_FACEBOOK_APP to BuildConfig.DEFAULT_FACEBOOK_APP_ID,
            KEY_FACEBOOK_TOKEN to BuildConfig.DEFAULT_FACEBOOK_TOKEN,
            KEY_OPENAI to BuildConfig.DEFAULT_OPENAI_KEY,
            KEY_WEATHER to BuildConfig.DEFAULT_WEATHER_KEY
        )
    }

    init {
        initializeDefaults()
    }

    private fun initializeDefaults() {
        try {
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) {
                    DEBUG_FETCH_INTERVAL
                } else {
                    DEFAULT_FETCH_INTERVAL
                }
            }
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(DEFAULT_CONFIG)
            Log.d(TAG, "Remote config defaults initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize remote config defaults", e)
        }
    }

    suspend fun ensureInitialized(): Boolean = try {
        withTimeout(DEFAULT_TIMEOUT_SECONDS.seconds) {
            if (isInitialized) return@withTimeout true

            suspendCancellableCoroutine { continuation ->
                remoteConfig.fetchAndActivate()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            isInitialized = true
                            logConfigValues()
                            continuation.resume(true)
                        } else {
                            Log.e(TAG, "Remote config initialization failed", task.exception)
                            continuation.resume(false)
                        }
                    }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error ensuring remote config initialization", e)
        false
    }

    private fun logConfigValues() {
        Log.d(TAG, """
            Remote Config Values:
            Maps API Key Valid: ${isValidMapsKey(getMapsKey())}
            Google Sign In Key Present: ${getGoogleSignInKey().isNotBlank()}
            Recaptcha Key Present: ${getRecaptchaKey().isNotBlank()}
            Facebook App ID Present: ${getFacebookAppId().isNotBlank()}
            Facebook Token Present: ${getFacebookClientToken().isNotBlank()}
            Weather Key Present: ${getWeatherKey().isNotBlank()}
        """.trimIndent())
    }

    private fun isValidMapsKey(key: String): Boolean {
        return key.isNotBlank() &&
                key.startsWith("AIza") &&
                key.length >= 39 &&
                !key.contains("YOUR-KEY-HERE") &&
                !key.contains("default") &&
                !key.contains("placeholder")
    }

    // Improved API Key Getters with validation
    // In RemoteConfigManager.kt, update getMapsKey():
    fun getMapsKey(): String {
        val key = remoteConfig.getString(KEY_MAPS)
        Log.d(TAG, """
        Maps Key Status:
        Key Length: ${key.length}
        First/Last 5: ${key.take(5)}...${key.takeLast(5)}
        Valid Format: ${isValidMapsKey(key)}
        From Default: ${key == BuildConfig.DEFAULT_MAPS_API_KEY}
    """.trimIndent())
        return key
    }

    fun getGoogleSignInKey(): String = getConfigValue(KEY_GOOGLE_SIGN_IN)
    fun getRecaptchaKey(): String = getConfigValue(KEY_RECAPTCHA)
    fun getFacebookAppId(): String = getConfigValue(KEY_FACEBOOK_APP)
    fun getFacebookClientToken(): String = getConfigValue(KEY_FACEBOOK_TOKEN)
    fun getOpenAIKey(): String = getConfigValue(KEY_OPENAI)
    fun getWeatherKey(): String = getConfigValue(KEY_WEATHER)

    private fun getConfigValue(key: String): String {
        return try {
            if (!isInitialized) return DEFAULT_CONFIG[key] ?: ""
            val value = remoteConfig.getString(key)
            value.ifBlank { DEFAULT_CONFIG[key] ?: "" }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving config value for key: $key", e)
            DEFAULT_CONFIG[key] ?: ""
        }
    }

    // For testing and debugging
    @Throws(IllegalStateException::class)
    suspend fun forceRefresh(): Boolean = try {
        withTimeout(DEFAULT_TIMEOUT_SECONDS.seconds) {
            suspendCancellableCoroutine { continuation ->
                remoteConfig.fetch(0)
                    .continueWithTask { remoteConfig.activate() }
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            logConfigValues()
                            continuation.resume(true)
                        } else {
                            Log.e(TAG, "Force refresh failed", task.exception)
                            continuation.resume(false)
                        }
                    }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error during force refresh", e)
        false
    }
}
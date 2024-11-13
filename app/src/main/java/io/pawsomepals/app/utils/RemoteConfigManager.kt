package io.pawsomepals.app.utils

import android.content.ContentValues.TAG
import android.util.Log
import io.pawsomepals.app.BuildConfig  // Add this import

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor() {
    // Get Firebase Remote Config instance
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    companion object {
        const val KEY_RECAPTCHA = "RECAPTCHA_SITE_KEY"
        const val KEY_MAPS = "MAPS_API_KEY"
        const val KEY_GOOGLE_SIGN_IN = "GOOGLE_SIGN_IN_API_KEY"
        const val KEY_FACEBOOK_APP = "FACEBOOK_APP_ID"
        const val KEY_FACEBOOK_TOKEN = "FACEBOOK_CLIENT_TOKEN"
        const val KEY_OPENAI = "OPENAI_API_KEY"
        const val KEY_WEATHER = "WEATHER_API_KEY"

        // Default fetch interval
        private const val DEFAULT_FETCH_INTERVAL = 3600L // 1 hour
    }

    init {
        setupDefaultConfig()
    }

    private fun setupDefaultConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else DEFAULT_FETCH_INTERVAL
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf(
            KEY_RECAPTCHA to "6LcM4FUqAAAAAPw2npJ8wOOsaSlqw8B_ju6CT67i",
            KEY_MAPS to "AIzaSyCUaMjo8iY2B6roaMJnAx2CVBfBx7zgw6E",
            KEY_GOOGLE_SIGN_IN to "455388576182-nb7hkru3benl1qe38epcccjd0kurebtl.apps.googleusercontent.com",
            KEY_FACEBOOK_APP to "527141699750137",
            KEY_FACEBOOK_TOKEN to "5f8a74941436f7da5230b70876731479",
            KEY_OPENAI to "sk-OBO10XT3CzWpM-ulVeVmWU-EMVnO5wx2jb1IcmZf2bT3BlbkFJA49zMWGgDY559Dd3mdPNBiM-47ilg5NvqnGJOpZQUA",
            KEY_WEATHER to "aa553d06e53c43aea30171022240810"
        )
        remoteConfig.setDefaultsAsync(defaults)
    }

    fun initialize() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Config params updated
            }
        }
    }
    fun fetchAndActivate(onComplete: (Boolean) -> Unit) {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Config params updated: ${task.result}")
                } else {
                    Log.e(TAG, "Config params update failed: ${task.exception?.message}")
                }
                onComplete(task.isSuccessful)
            }
    }

    // Getter methods
    fun getRecaptchaKey(): String = remoteConfig.getString(KEY_RECAPTCHA)
    fun getMapsKey(): String = remoteConfig.getString(KEY_MAPS)
    fun getGoogleSignInKey(): String = remoteConfig.getString(KEY_GOOGLE_SIGN_IN)
    fun getFacebookAppId(): String = remoteConfig.getString(KEY_FACEBOOK_APP)
    fun getFacebookClientToken(): String = remoteConfig.getString(KEY_FACEBOOK_TOKEN)
    fun getOpenAIKey(): String = remoteConfig.getString(KEY_OPENAI)
    fun getWeatherKey(): String = remoteConfig.getString(KEY_WEATHER)
}
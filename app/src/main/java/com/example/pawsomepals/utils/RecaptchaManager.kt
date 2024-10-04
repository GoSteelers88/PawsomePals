package com.example.pawsomepals.utils

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.pawsomepals.BuildConfig
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecaptchaManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private suspend fun getRecaptchaClient(): Result<RecaptchaClient> = withContext(Dispatchers.IO) {
        try {
            Recaptcha.getClient(context as Application, BuildConfig.RECAPTCHA_SITE_KEY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get RecaptchaClient", e)
            Result.failure(e)
        }
    }

    suspend fun verifyRecaptcha(action: String): String = withContext(Dispatchers.IO) {
        getRecaptchaClient().mapCatching { client ->
            val recaptchaAction = when (action) {
                "login" -> RecaptchaAction.LOGIN
                "register" -> RecaptchaAction.SIGNUP
                else -> RecaptchaAction.custom(action)
            }
            client.execute(recaptchaAction)
        }.getOrThrow() // This will throw an exception if there's an error
    }.toString()

    suspend fun executeRecaptcha(action: String): Boolean {
        return try {
            val token = verifyRecaptcha(action)
            token.isNotEmpty().also { isValid ->
                Log.d(TAG, "reCAPTCHA token validation result: $isValid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "reCAPTCHA verification failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "RecaptchaManager"
    }
}
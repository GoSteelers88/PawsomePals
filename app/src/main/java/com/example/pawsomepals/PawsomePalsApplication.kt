package com.example.pawsomepals

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.safetynet.SafetyNetAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PawsomePalsApplication : Application(), Configuration.Provider {

    companion object {
        lateinit var instance: PawsomePalsApplication
            private set
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    lateinit var firestore: FirebaseFirestore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeFirebase()
        initializeAppCheck()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:455388576182:android:fcb4e2564b1dff93192917")
                    .setApiKey("AIzaSyCVB4RyLBVeFtFECVm-Qu4ruVGhGWvJuGs")
                    .setProjectId("pawsome-pals")
                    .setDatabaseUrl("https://pawsome-pals-default-rtdb.firebaseio.com/")
                    .build()

                FirebaseApp.initializeApp(this, options)
            }

            firestore = Firebase.firestore

            Log.d("PawsomePals", "Firebase initialized successfully")
            Log.d("PawsomePals", "Firebase apps: ${FirebaseApp.getApps(this).map { it.name }}")
            Log.d("PawsomePals", "Default Firebase app: ${FirebaseApp.getInstance().name}")
            Log.d("PawsomePals", "Firestore instance: $firestore")
        } catch (e: Exception) {
            Log.e("PawsomePals", "Firebase initialization failed", e)
            e.printStackTrace()
        }
    }

    private fun initializeAppCheck() {
        try {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d("PawsomePals", "Firebase App Check initialized successfully")
        } catch (e: Exception) {
            Log.e("PawsomePals", "Firebase App Check initialization failed", e)
            e.printStackTrace()
        }
    }

    fun testFirestoreConnection() {
        firestore.collection("test").document("test")
            .set(hashMapOf("test" to "test"))
            .addOnSuccessListener {
                Log.d("PawsomePals", "Firestore write successful")
            }
            .addOnFailureListener { e ->
                Log.e("PawsomePals", "Firestore write failed", e)
            }
    }
}
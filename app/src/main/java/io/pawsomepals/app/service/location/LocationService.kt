package io.pawsomepals.app.service.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.data.model.Dog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    class LocationException(message: String) : Exception(message)

    private var lastKnownLocation: Location? = null

    suspend fun getLastKnownLocation(): Location? {
        return lastKnownLocation ?: getCurrentLocation()?.also {
            lastKnownLocation = it
        }
    }

    suspend fun getDogLocation(dogId: String): LatLng? {
        return try {
            // First try to get from Firestore
            val dogDoc = firestore.collection("dogs")
                .document(dogId)
                .get()
                .await()

            if (dogDoc.exists()) {
                val latitude = dogDoc.getDouble("latitude")
                val longitude = dogDoc.getDouble("longitude")

                if (latitude != null && longitude != null) {
                    return LatLng(latitude, longitude)
                }
            }

            // If no location in Firestore, try to get owner's location
            val ownerId = dogDoc.getString("ownerId")
            if (ownerId != null) {
                val userDoc = firestore.collection("users")
                    .document(ownerId)
                    .get()
                    .await()

                val latitude = userDoc.getDouble("latitude")
                val longitude = userDoc.getDouble("longitude")

                if (latitude != null && longitude != null) {
                    return LatLng(latitude, longitude)
                }
            }

            // If still no location, try to get current location
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    return LatLng(location.latitude, location.longitude)
                }
            }

            null // Return null if no location found
        } catch (e: Exception) {
            null // Return null on any error
        }
    }

    suspend fun filterProfilesByDistance(
        profiles: List<Dog>,
        maxDistance: Double
    ): List<Dog> {
        val currentLocation = getLastKnownLocation() ?: return profiles

        return profiles.filter { dog ->
            val distance = calculateDistance(
                currentLocation.latitude,
                currentLocation.longitude,
                dog.latitude ?: return@filter true,
                dog.longitude ?: return@filter true
            )
            distance <= maxDistance
        }
    }

    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(location)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] / 1000 // Convert meters to kilometers
    }

    fun calculateDistancesForProfiles(
        currentLocation: Location,
        profiles: List<Dog>
    ): Map<String, Double> {
        return profiles.associate { dog ->
            // Store latitude and longitude in local variables
            val latitude = dog.latitude
            val longitude = dog.longitude

            val distance = if (latitude != null && longitude != null) {
                calculateDistance(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    latitude,
                    longitude
                ).toDouble()
            } else {
                Double.POSITIVE_INFINITY
            }

            dog.id to distance
        }
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private const val DEFAULT_RADIUS = 50.0 // kilometers
    }
}
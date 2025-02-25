package io.pawsomepals.app.service.location

import android.content.Context
import android.location.Location
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.data.model.DogFriendlyLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationSuggestionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val placesClient: PlacesClient,  // Injected from our PlacesModule
    private val firestore: FirebaseFirestore
) {



    suspend fun getDogFriendlyLocations(location1: com.google.android.gms.maps.model.LatLng, location2: com.google.android.gms.maps.model.LatLng): List<Place> = suspendCancellableCoroutine { continuation ->
        val placeFields = listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val request = FindCurrentPlaceRequest.newInstance(placeFields)

        placesClient.findCurrentPlace(request).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val response = task.result
                val dogFriendlyPlaces = response?.placeLikelihoods
                    ?.filter { it.place.types?.contains(Place.Type.PARK) == true }
                    ?.map { it.place }
                    ?: emptyList()
                continuation.resume(dogFriendlyPlaces)
            } else {
                continuation.resumeWithException(task.exception ?: Exception("Failed to get places"))
            }
        }
    }

    suspend fun getMidpointLocations(
        location1: com.google.android.gms.maps.model.LatLng,
        location2: com.google.android.gms.maps.model.LatLng
    ): List<DogFriendlyLocation> {
        // Create midpoint using Google Maps LatLng
        val midpoint = com.google.android.gms.maps.model.LatLng(
            (location1.latitude + location2.latitude) / 2,
            (location1.longitude + location2.longitude) / 2
        )

        return getDogFriendlyLocations(midpoint, midpoint).map { place ->
            DogFriendlyLocation.fromPlace(place).copy(
                isPlaydateEnabled = true,
                distanceFromMidpoint = calculateDistance(
                    midpoint,
                    place.latLng ?: com.google.android.gms.maps.model.LatLng(0.0, 0.0)
                )
            )
        }
    }

    suspend fun getSavedLocations(userId: String): List<DogFriendlyLocation> =
        suspendCancellableCoroutine { continuation ->
            firestore.collection("users")
                .document(userId)
                .collection("saved_locations")
                .get()
                .addOnSuccessListener { snapshot ->
                    val locations = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(DogFriendlyLocation::class.java)
                    }
                    continuation.resume(locations)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    fun calculateDistance(point1: com.google.android.gms.maps.model.LatLng,
                          point2: com.google.android.gms.maps.model.LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }

    suspend fun saveLocation(userId: String, location: DogFriendlyLocation) =
        suspendCancellableCoroutine { continuation ->
            firestore.collection("users")
                .document(userId)
                .collection("saved_locations")
                .add(location)
                .addOnSuccessListener {
                    continuation.resume(Unit)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
}
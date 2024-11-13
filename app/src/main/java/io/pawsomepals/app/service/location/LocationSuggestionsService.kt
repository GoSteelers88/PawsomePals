package io.pawsomepals.app.service.location

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.model.DogFriendlyLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationSuggestionService @Inject constructor(
    private val context: Context,
    private val firestore: FirebaseFirestore
) {
    private val placesClient: PlacesClient

    init {
        Places.initialize(context, "YOUR_API_KEY_HERE")
        placesClient = Places.createClient(context)
    }

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
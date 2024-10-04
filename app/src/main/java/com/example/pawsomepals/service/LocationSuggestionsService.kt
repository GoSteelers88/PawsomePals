package com.example.pawsomepals.service

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationSuggestionService(private val context: Context) {
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
}
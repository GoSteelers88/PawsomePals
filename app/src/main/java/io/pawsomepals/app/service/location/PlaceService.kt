package io.pawsomepals.app.service.location

import android.net.Uri
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import android.content.Context // Change this import
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PlaceService @Inject constructor(
    private val placesClient: PlacesClient,
    private val remoteConfigManager: RemoteConfigManager,
    @ApplicationContext private val context: Context // Add this

) {
    init {
        // Initialize Places if not already initialized
        if (!Places.isInitialized()) {
            Places.initialize(context, remoteConfigManager.getMapsKey())
        }
    }

    suspend fun searchNearbyDogFriendlyPlaces(
        center: LatLng,
        radius: Double = 5000.0 // 5km default radius
    ): List<DogFriendlyLocation> {
        val bounds = LatLngBounds.builder()
            .include(calculateLatLng(center, radius, 0.0))   // North
            .include(calculateLatLng(center, radius, 90.0))  // East
            .include(calculateLatLng(center, radius, 180.0)) // South
            .include(calculateLatLng(center, radius, 270.0)) // West
            .build()

        val request = FindCurrentPlaceRequest.newInstance(listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.TYPES,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI
        ))

        return try {
            placesClient.findCurrentPlace(request)
                .await()
                .placeLikelihoods
                .filter { isRelevantPlace(it.place) }
                .map { convertToLocation(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isRelevantPlace(place: Place): Boolean {
        val relevantTypes = setOf(
            Place.Type.PARK,
            Place.Type.PET_STORE,
            Place.Type.VETERINARY_CARE
        )
        return place.types?.any { it in relevantTypes } == true
    }

    private fun convertToLocation(placeLikelihood: PlaceLikelihood): DogFriendlyLocation {
        val place = placeLikelihood.place
        return DogFriendlyLocation(
            placeId = place.id ?: "",
            name = place.name ?: "",
            address = place.address ?: "",
            latitude = place.latLng?.latitude ?: 0.0,
            longitude = place.latLng?.longitude ?: 0.0,
            placeTypes = place.types?.map { it.name } ?: emptyList(),
            rating = place.rating?.toDouble(),
            userRatingsTotal = place.userRatingsTotal?.toInt(),
            phoneNumber = place.phoneNumber,
            websiteUri = place.websiteUri?.toString(),
            // Default values for dog-specific attributes
            isDogPark = place.types?.contains(Place.Type.PARK) == true,
            isOffLeashAllowed = false,
            hasFencing = false,
            hasWaterFountain = false,
            hasWasteStations = true,
            amenities = generateDefaultAmenities(place),
            restrictions = generateDefaultRestrictions()
        )
    }

    private fun generateDefaultAmenities(place: Place): List<String> {
        val amenities = mutableListOf<String>()
        if (place.types?.contains(Place.Type.PARK) == true) {
            amenities.addAll(listOf(
                DogFriendlyLocation.Amenity.WATER_FOUNTAIN.name,
                DogFriendlyLocation.Amenity.WASTE_STATIONS.name,
                DogFriendlyLocation.Amenity.SEATING.name
            ))
        }
        return amenities
    }

    private fun generateDefaultRestrictions(): List<String> {
        return listOf(
            DogFriendlyLocation.Restriction.LEASH_REQUIRED.name,
            DogFriendlyLocation.Restriction.VACCINATION_REQUIRED.name
        )
    }

    private fun calculateLatLng(center: LatLng, radius: Double, bearing: Double): LatLng {
        val R = 6378137.0 // Earth's radius in meters
        val d = radius
        val bearingRad = Math.toRadians(bearing)
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)

        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(d / R) +
                    Math.cos(lat1) * Math.sin(d / R) * Math.cos(bearingRad)
        )

        val lon2 = lon1 + Math.atan2(
            Math.sin(bearingRad) * Math.sin(d / R) * Math.cos(lat1),
            Math.cos(d / R) - Math.sin(lat1) * Math.sin(lat2)
        )

        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
}
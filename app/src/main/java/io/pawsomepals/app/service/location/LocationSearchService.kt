package io.pawsomepals.app.service.location

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.DogFriendlyLocation.VenueType
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.Math.toRadians
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

@Singleton
class LocationSearchService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val placesInitializer: PlacesInitializer,
    private val locationProvider: LocationProvider,
    private val remoteConfigManager: RemoteConfigManager
) {
    private val clientMutex = Mutex()
    private var placesClient: PlacesClient? = null
    private var sessionToken: AutocompleteSessionToken? = null

    sealed class SearchResult<out T> {
        data class Success<T>(val data: T) : SearchResult<T>()
        data class Error(val exception: Throwable) : SearchResult<Nothing>()
        object Loading : SearchResult<Nothing>()
    }

    data class LocationFilters(
        val venueTypes: Set<VenueType> = emptySet(),
        val outdoorOnly: Boolean = true,
        val offLeashAllowed: Boolean = false,
        val hasParking: Boolean = false,
        val waterAvailable: Boolean = false,
        val minRating: Double? = null
    )

    suspend fun searchDogFriendlyLocations(
        radius: Double = DEFAULT_RADIUS,
        filters: LocationFilters = LocationFilters()
    ): Flow<SearchResult<List<DogFriendlyLocation>>> = flow {
        emit(SearchResult.Loading)

        try {
            val client = getPlacesClient()
            val currentLocation = withTimeout(10000) {
                locationProvider.getCurrentLocation()
            } ?: throw LocationException("Unable to get current location")

            val location = LatLng(currentLocation.latitude, currentLocation.longitude)
            val bounds = createBoundsFromLocation(location, radius)
            val placeTypes = getDogFriendlyPlaceTypes(filters)

            val request = FindAutocompletePredictionsRequest.builder()
                .setLocationBias(RectangularBounds.newInstance(bounds))
                .setTypesFilter(placeTypes)
                .setSessionToken(getOrCreateSessionToken())
                .build()

            val predictions = withContext(Dispatchers.IO) {
                client.findAutocompletePredictions(request).await()
            }

            val places = coroutineScope {
                predictions.autocompletePredictions
                    .chunked(SEARCH_BATCH_SIZE)
                    .flatMap { chunk ->
                        chunk.map { prediction ->
                            async { fetchPlaceDetails(prediction.placeId, location) }
                        }
                            .awaitAll()
                            .filterNotNull()
                    }
                    .filter { place -> applyFilters(place, filters) }
                    .sortedByDescending { getDogFriendlyScore(it, location) }
            }

            emit(SearchResult.Success(places))
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchDogFriendlyLocations: ${e.message}", e)
            emit(SearchResult.Error(e))
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in searchDogFriendlyLocations", e)
        emit(SearchResult.Error(e))
    }

    private suspend fun getPlacesClient(): PlacesClient = clientMutex.withLock {
        placesClient?.let { return it }

        placesInitializer.ensureInitialized()
        return Places.createClient(context).also { client ->
            try {
                val testRequest = FindAutocompletePredictionsRequest.builder()
                    .setQuery("test")
                    .setSessionToken(getOrCreateSessionToken())
                    .build()

                withTimeout(5000) {
                    client.findAutocompletePredictions(testRequest).await()
                }
                placesClient = client
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify Places client", e)
                throw e
            }
        }
    }

    private fun getOrCreateSessionToken(): AutocompleteSessionToken {
        return sessionToken ?: AutocompleteSessionToken.newInstance().also {
            sessionToken = it
        }
    }

    private fun getDogFriendlyPlaceTypes(filters: LocationFilters): List<String> {
        val baseTypes = if (filters.venueTypes.isEmpty()) {
            listOf(
                Place.Type.PARK,
                Place.Type.PET_STORE,
                Place.Type.BAR,
                Place.Type.CAFE,
                Place.Type.RESTAURANT,
                Place.Type.TOURIST_ATTRACTION
            )
        } else {
            filters.venueTypes.map { venueType ->
                when (venueType) {
                    VenueType.DOG_PARK, VenueType.PUBLIC_PARK -> Place.Type.PARK
                    VenueType.RESTAURANT -> Place.Type.RESTAURANT
                    VenueType.BAR, VenueType.BREWERY -> Place.Type.BAR
                    VenueType.CAFE -> Place.Type.CAFE
                    VenueType.PET_STORE, VenueType.DOG_DAYCARE -> Place.Type.PET_STORE
                    VenueType.BEACH -> Place.Type.NATURAL_FEATURE
                    VenueType.HIKING_TRAIL -> Place.Type.PARK
                    VenueType.BOTANICAL_GARDEN -> Place.Type.TOURIST_ATTRACTION
                    else -> Place.Type.PARK
                }
            }
        }
        return baseTypes.map { it.toString().lowercase() }
    }

    private suspend fun fetchPlaceDetails(
        placeId: String,
        referenceLocation: LatLng
    ): DogFriendlyLocation? {
        return try {
            withContext(Dispatchers.IO) {
                val client = getPlacesClient()
                val request = FetchPlaceRequest.builder(placeId, DEFAULT_PLACE_FIELDS).build()
                val place = withTimeout(5000) {
                    client.fetchPlace(request).await().place
                }
                createDogFriendlyLocation(place, referenceLocation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching place details for $placeId: ${e.message}")
            null
        }
    }

    private fun createDogFriendlyLocation(
        place: Place,
        referenceLocation: LatLng
    ): DogFriendlyLocation {
        return DogFriendlyLocation.fromPlace(
            place = place,
            distanceMeters = calculateDistance(
                referenceLocation,
                place.latLng ?: LatLng(0.0, 0.0)
            ).toDouble()
        )
    }

    private fun getDogFriendlyScore(place: DogFriendlyLocation, referenceLocation: LatLng): Double {
        var score = 0.0

        // Base score from ratings
        score += (place.rating ?: 0.0) * 2
        score += (place.userRatingsTotal ?: 0) / 100.0

        // Venue type scoring
        when {
            place.isDogPark -> score += 10.0
            place.placeTypes.contains("park") -> score += 8.0
            place.placeTypes.contains("pet_store") -> score += 7.0
            (place.placeTypes.contains("bar") ||
                    place.placeTypes.contains("restaurant") ||
                    place.placeTypes.contains("cafe")) &&
                    place.hasOutdoorSeating -> score += 6.0
        }

        // Feature scoring
        if (place.hasOutdoorSeating) score += 3.0
        if (place.hasWaterFountain) score += 2.0
        if (place.hasParking) score += 2.0
        if (place.hasWasteStations) score += 2.0
        if (place.hasFencing && place.isOffLeashAllowed) score += 4.0
        if (!place.isIndoor) score += 2.0

        // Distance penalty (closer is better)
        val distanceMeters = calculateDistance(place.getLatLng(), referenceLocation)
        score += maxOf(0.0, 10.0 - (distanceMeters / 500.0))

        return score
    }

    private fun applyFilters(
        location: DogFriendlyLocation,
        filters: LocationFilters
    ): Boolean {
        if (filters.outdoorOnly && location.isIndoor) return false
        if (filters.offLeashAllowed && !location.isOffLeashAllowed) return false
        if (filters.hasParking && !location.hasParking) return false
        if (filters.waterAvailable && !location.hasWaterFountain) return false
        if (filters.minRating != null && (location.rating ?: 0.0) < filters.minRating) return false

        return filters.venueTypes.isEmpty() || filters.venueTypes.any { venueType ->
            matchesVenueType(location, venueType)
        }
    }

    private fun matchesVenueType(location: DogFriendlyLocation, venueType: VenueType): Boolean {
        val types = location.placeTypes.map { it.lowercase() }
        return when (venueType) {
            VenueType.DOG_PARK -> location.isDogPark
            VenueType.PUBLIC_PARK -> types.contains("park")
            VenueType.RESTAURANT -> types.contains("restaurant")
            VenueType.BAR, VenueType.BREWERY -> types.contains("bar")
            VenueType.CAFE -> types.contains("cafe")
            VenueType.PET_STORE -> types.contains("pet_store")
            VenueType.BEACH -> types.contains("natural_feature")
            VenueType.HIKING_TRAIL -> types.contains("park")
            VenueType.BOTANICAL_GARDEN -> types.contains("tourist_attraction")
            else -> false
        }
    }

    private fun createBoundsFromLocation(center: LatLng, radiusInKm: Double): LatLngBounds {
        val radiusInMeters = minOf(radiusInKm * 1000.0, MAX_RADIUS_METERS)
        val radiusInDegrees = radiusInMeters / 111000.0
        val latDistance = radiusInDegrees
        val lngDistance = radiusInDegrees / cos(toRadians(center.latitude))

        return LatLngBounds(
            LatLng(center.latitude - latDistance, center.longitude - lngDistance),
            LatLng(center.latitude + latDistance, center.longitude + lngDistance)
        )
    }

    public fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude,
            point1.longitude,
            point2.latitude,
            point2.longitude,
            results
        )
        return results[0]
    }

    class LocationException(message: String) : Exception(message)

    companion object {
        private const val TAG = "LocationSearchService"
        private const val DEFAULT_RADIUS = 5.0 // 5km
        private const val MAX_RADIUS_METERS = 50000.0 // 50km max
        private const val SEARCH_BATCH_SIZE = 5

        private val DEFAULT_PLACE_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.RATING,
            Place.Field.TYPES,
            Place.Field.PHOTO_METADATAS,
            Place.Field.OPENING_HOURS,
            Place.Field.WEBSITE_URI,
            Place.Field.BUSINESS_STATUS,
            Place.Field.PRICE_LEVEL,
            Place.Field.USER_RATINGS_TOTAL
        )
    }
}
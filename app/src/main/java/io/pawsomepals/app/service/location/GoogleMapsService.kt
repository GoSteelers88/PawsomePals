package io.pawsomepals.app.service.location

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleMapsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val placesClient: PlacesClient by lazy {
        Places.createClient(context)
    }

    // Cache for place details to minimize API calls
    private val placeCache = LruCache<String, Place>(100)

    // Cache for photos to minimize API calls and memory usage
    private val photoCache = LruCache<String, PhotoResult>(50)

    // Rate limiting tracking
    private val rateLimiter = RateLimiter(
        maxRequests = 100,
        perTimeWindow = TimeUnit.MINUTES.toMillis(1)
    )

    sealed class PlacesResult<out T> {
        data class Success<T>(val data: T) : PlacesResult<T>()
        data class Error(val exception: Exception) : PlacesResult<Nothing>()
    }

    data class PhotoResult(
        val bitmap: Bitmap,
        val attribution: String?
    )

    suspend fun searchNearbyDogFriendlyPlaces(
        location: LatLng,
        radius: Double
    ): Flow<PlacesResult<List<Place>>> = flow {
        try {
            rateLimiter.checkRateLimit()

            val request = createNearbySearchRequest(location, radius)
            val response = withContext(Dispatchers.IO) {
                placesClient.findCurrentPlace(request).await()
            }

            val dogFriendlyPlaces = response.placeLikelihoods
                .filter { isPlaceDogFriendly(it.place) }
                .map { it.place }
                .also { places ->
                    // Cache the places
                    places.forEach { place ->
                        placeCache.put(place.id, place)
                    }
                }

            emit(PlacesResult.Success(dogFriendlyPlaces))
        } catch (e: Exception) {
            emit(handlePlacesError(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getPlaceDetails(placeId: String): PlacesResult<Place> {
        return try {
            rateLimiter.checkRateLimit()

            // Check cache first
            placeCache.get(placeId)?.let {
                return PlacesResult.Success(it)
            }

            val request = createPlaceDetailsRequest(placeId)
            val response = withContext(Dispatchers.IO) {
                placesClient.fetchPlace(request).await()
            }

            val place = response.place
            placeCache.put(placeId, place)
            PlacesResult.Success(place)
        } catch (e: Exception) {
            handlePlacesError(e)
        }
    }

    suspend fun fetchPhoto(photoMetadata: PhotoMetadata): PlacesResult<PhotoResult> {
        return try {
            rateLimiter.checkRateLimit()

            // Check cache first
            photoCache.get(photoMetadata.attributions.toString())?.let {
                return PlacesResult.Success(it)
            }

            val request = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(1200)
                .setMaxHeight(800)
                .build()

            val response = withContext(Dispatchers.IO) {
                placesClient.fetchPhoto(request).await()
            }

            val photoResult = PhotoResult(
                bitmap = response.bitmap,
                attribution = photoMetadata.attributions
            )

            // Cache the photo
            photoCache.put(photoMetadata.attributions.toString(), photoResult)
            PlacesResult.Success(photoResult)
        } catch (e: Exception) {
            handlePlacesError(e)
        }
    }

    suspend fun getAutocompleteResults(
        query: String,
        location: LatLng,
        radius: Double
    ): PlacesResult<List<AutocompletePrediction>> {
        return try {
            rateLimiter.checkRateLimit()

            val bounds = RectangularBounds.newInstance(
                createBoundsFromLocation(location, radius)
            )

            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setLocationBias(bounds)
                .setTypesFilter(listOf(
                    Place.Type.PARK.name,
                    Place.Type.RESTAURANT.name,
                    Place.Type.BAR.name,
                    Place.Type.CAFE.name
                ))
                .build()

            val response = withContext(Dispatchers.IO) {
                placesClient.findAutocompletePredictions(request).await()
            }

            PlacesResult.Success(response.autocompletePredictions)
        } catch (e: Exception) {
            handlePlacesError(e)
        }
    }

    private fun createNearbySearchRequest(location: LatLng, radius: Double): FindCurrentPlaceRequest {
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.PHOTO_METADATAS,
            Place.Field.OPENING_HOURS,
            Place.Field.WEBSITE_URI,
            Place.Field.PHONE_NUMBER
        )

        return FindCurrentPlaceRequest.newInstance(placeFields)
    }

    private fun createPlaceDetailsRequest(placeId: String) =
        com.google.android.libraries.places.api.net.FetchPlaceRequest.builder(
            placeId,
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.TYPES,
                Place.Field.RATING,
                Place.Field.USER_RATINGS_TOTAL,
                Place.Field.PHOTO_METADATAS,
                Place.Field.OPENING_HOURS,
                Place.Field.WEBSITE_URI,
                Place.Field.PHONE_NUMBER
            )
        ).build()

    private fun isPlaceDogFriendly(place: Place): Boolean {
        val dogFriendlyTypes = setOf(
            Place.Type.PARK,
            Place.Type.PET_STORE,
            Place.Type.RESTAURANT,
            Place.Type.CAFE,
            Place.Type.BAR
        )
        return place.types?.any { it in dogFriendlyTypes } ?: false
    }

    private fun createBoundsFromLocation(center: LatLng, radiusInMeters: Double): LatLngBounds {
        val latRadian = Math.toRadians(center.latitude)
        val degLatKm = 110.574235
        val degLongKm = 110.572833 * Math.cos(latRadian)
        val deltaLat = radiusInMeters / 1000.0 / degLatKm
        val deltaLong = radiusInMeters / 1000.0 / degLongKm

        val southWest = LatLng(
            center.latitude - deltaLat,
            center.longitude - deltaLong
        )
        val northEast = LatLng(
            center.latitude + deltaLat,
            center.longitude + deltaLong
        )

        return LatLngBounds(southWest, northEast)
    }


    private fun handlePlacesError(e: Exception): PlacesResult.Error {
        return when (e) {
            is ApiException -> PlacesResult.Error(Exception("Places API error: ${e.statusCode}"))
            is SecurityException -> PlacesResult.Error(Exception("Location permission denied"))
            else -> PlacesResult.Error(Exception("Error accessing Places API: ${e.message}"))
        }
    }

    companion object {
        private const val TAG = "GoogleMapsService"
    }
}

private class RateLimiter(
    private val maxRequests: Int,
    private val perTimeWindow: Long
) {
    private val requestTimestamps = mutableListOf<Long>()

    @Synchronized
    fun checkRateLimit() {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - perTimeWindow

        // Remove old timestamps
        requestTimestamps.removeAll { it < windowStart }

        // Check if we're over the limit
        if (requestTimestamps.size >= maxRequests) {
            throw RateLimitExceededException("Rate limit exceeded: $maxRequests requests per ${perTimeWindow}ms")
        }

        // Add new timestamp
        requestTimestamps.add(currentTime)
    }
}

class RateLimitExceededException(message: String) : Exception(message)
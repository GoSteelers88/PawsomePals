package io.pawsomepals.app.data.repository

import android.util.Log
import io.pawsomepals.app.data.dao.DogFriendlyLocationDao
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.utils.LocationMapper
import io.pawsomepals.app.utils.LocationValidator
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val locationDao: DogFriendlyLocationDao,
    private val locationSearchService: LocationSearchService,
    private val locationMapper: LocationMapper,
    private val locationValidator: LocationValidator,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "LocationRepository"
        private const val LOCATIONS_COLLECTION = "dog_friendly_locations"
        private const val CACHE_DURATION = 24 * 60 * 60 * 1000 // 24 hours in milliseconds
    }

    sealed class LocationResult<out T> {
        data class Success<T>(val data: T) : LocationResult<T>()
        data class Error(val exception: Exception) : LocationResult<Nothing>()
        object Loading : LocationResult<Nothing>()
    }

    fun searchNearbyLocations(
        location: LatLng,
        radius: Double,
        filters: LocationSearchService.LocationFilters = LocationSearchService.LocationFilters()
    ): Flow<LocationResult<List<DogFriendlyLocation>>> = flow {
        emit(LocationResult.Loading)
        try {
            // First, check local cache
            val cachedLocations = locationDao.getNearbyLocations(
                location.latitude,
                location.longitude,
                radius
            )

            if (cachedLocations.isNotEmpty() && !isCacheExpired(cachedLocations.first())) {
                emit(LocationResult.Success(cachedLocations))
            }

            // Fetch from network
            locationSearchService.searchDogFriendlyLocations(location, radius, filters)
                .collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            val validatedLocations = validateAndFilterLocations(result.data)
                            // Update cache
                            locationDao.insertAll(validatedLocations)
                            // Update Firestore
                            updateFirestoreLocations(validatedLocations)
                            emit(LocationResult.Success(validatedLocations))
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            emit(LocationResult.Error(result.exception))
                        }
                        is LocationSearchService.SearchResult.Loading -> {
                            // Already emitted Loading state
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching nearby locations", e)
            emit(LocationResult.Error(e))
        }
    }

    suspend fun getLocationDetails(placeId: String): LocationResult<DogFriendlyLocation> {
        return try {
            // Check local cache first
            val cachedLocation = locationDao.getLocationById(placeId)
            if (cachedLocation != null && !isCacheExpired(cachedLocation)) {
                return LocationResult.Success(cachedLocation)
            }

            // Fetch from network
            when (val result = locationSearchService.getLocationDetails(placeId)) {
                is LocationSearchService.SearchResult.Success -> {
                    val validatedLocation = validateLocation(result.data)
                    // Update cache
                    locationDao.insert(validatedLocation)
                    // Update Firestore
                    updateFirestoreLocation(validatedLocation)
                    LocationResult.Success(validatedLocation)
                }
                is LocationSearchService.SearchResult.Error -> {
                    LocationResult.Error(result.exception)
                }
                is LocationSearchService.SearchResult.Loading -> {
                    LocationResult.Loading
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location details", e)
            LocationResult.Error(e)
        }
    }

    fun getLocationsByType(
        venueType: DogFriendlyLocation.VenueType,
        location: LatLng,
        radius: Double
    ): Flow<LocationResult<List<DogFriendlyLocation>>> = flow {
        emit(LocationResult.Loading)
        try {
            locationSearchService.searchByVenueType(location, venueType, radius)
                .collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            val validatedLocations = validateAndFilterLocations(result.data)
                            locationDao.insertAll(validatedLocations)
                            emit(LocationResult.Success(validatedLocations))
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            emit(LocationResult.Error(result.exception))
                        }
                        is LocationSearchService.SearchResult.Loading -> {
                            // Already emitted Loading state
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting locations by type", e)
            emit(LocationResult.Error(e))
        }
    }

    fun getLocationsByAmenities(
        amenities: Set<DogFriendlyLocation.Amenity>,
        location: LatLng,
        radius: Double
    ): Flow<LocationResult<List<DogFriendlyLocation>>> = flow {
        emit(LocationResult.Loading)
        try {
            locationSearchService.searchByAmenities(location, amenities, radius)
                .collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            val validatedLocations = validateAndFilterLocations(result.data)
                            locationDao.insertAll(validatedLocations)
                            emit(LocationResult.Success(validatedLocations))
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            emit(LocationResult.Error(result.exception))
                        }
                        is LocationSearchService.SearchResult.Loading -> {
                            // Already emitted Loading state
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting locations by amenities", e)
            emit(LocationResult.Error(e))
        }
    }

    suspend fun reportLocationUpdate(
        placeId: String,
        updates: Map<String, Any>
    ): LocationResult<Unit> {
        return try {
            // Get current location data
            val location = locationDao.getLocationById(placeId)
                ?: return LocationResult.Error(Exception("Location not found"))

            // Apply updates
            val updatedLocation = applyUpdates(location, updates)

            // Validate updates
            val validationResult = locationValidator.validateLocation(updatedLocation)
            if (!validationResult.isValid) {
                return LocationResult.Error(
                    Exception("Invalid updates: ${validationResult.errors}")
                )
            }

            // Update local cache
            locationDao.insert(updatedLocation)

            // Update Firestore
            updateFirestoreLocation(updatedLocation)

            LocationResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting location update", e)
            LocationResult.Error(e)
        }
    }

    suspend fun getFavoriteLocations(): LocationResult<List<DogFriendlyLocation>> {
        return try {
            val favorites = locationDao.getFavoriteLocations()
            LocationResult.Success(favorites)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favorite locations", e)
            LocationResult.Error(e)
        }
    }

    private fun validateAndFilterLocations(
        locations: List<DogFriendlyLocation>
    ): List<DogFriendlyLocation> {
        return locations.filter { location ->
            val validationResult = locationValidator.validateLocation(location)
            validationResult.isValid
        }
    }

    private fun validateLocation(location: DogFriendlyLocation): DogFriendlyLocation {
        val validationResult = locationValidator.validateLocation(location)
        if (!validationResult.isValid) {
            Log.w(TAG, "Location validation warnings: ${validationResult.warnings}")
        }
        return location
    }

    private fun isCacheExpired(location: DogFriendlyLocation): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - location.lastUpdated) > CACHE_DURATION
    }

    private suspend fun updateFirestoreLocation(location: DogFriendlyLocation) {
        try {
            firestore.collection(LOCATIONS_COLLECTION)
                .document(location.placeId)
                .set(location)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Firestore location", e)
        }
    }

    private suspend fun updateFirestoreLocations(locations: List<DogFriendlyLocation>) {
        try {
            val batch = firestore.batch()
            locations.forEach { location ->
                val docRef = firestore.collection(LOCATIONS_COLLECTION)
                    .document(location.placeId)
                batch.set(docRef, location)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error batch updating Firestore locations", e)
        }
    }

    private fun applyUpdates(
        location: DogFriendlyLocation,
        updates: Map<String, Any>
    ): DogFriendlyLocation {
        return location.copy(
            hasWaterFountain = updates["hasWaterFountain"] as? Boolean ?: location.hasWaterFountain,
            hasWasteStations = updates["hasWasteStations"] as? Boolean ?: location.hasWasteStations,
            hasFencing = updates["hasFencing"] as? Boolean ?: location.hasFencing,
            isOffLeashAllowed = updates["isOffLeashAllowed"] as? Boolean ?: location.isOffLeashAllowed,
            hasOutdoorSeating = updates["hasOutdoorSeating"] as? Boolean ?: location.hasOutdoorSeating,
            lastUpdated = System.currentTimeMillis()
        )
    }

    suspend fun clearLocationCache() {
        locationDao.clearAll()
    }
}
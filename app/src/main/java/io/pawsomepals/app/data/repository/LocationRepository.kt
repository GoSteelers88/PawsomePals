package io.pawsomepals.app.data.repository

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.dao.DogFriendlyLocationDao
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.utils.LocationMapper
import io.pawsomepals.app.utils.LocationValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
        data class Error(val exception: Throwable) : LocationResult<Nothing>()
        object Loading : LocationResult<Nothing>()
    }

    fun searchNearbyLocations(
        location: LatLng,  // Keep this parameter for caching purposes
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

            // Fetch from network - note we only pass radius and filters now
            locationSearchService.searchDogFriendlyLocations(
                radius = radius,
                filters = filters
            ).collect { result ->
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
    suspend fun saveLocation(location: DogFriendlyLocation): LocationResult<Unit> {
        return try {
            // Update local cache
            locationDao.insert(location.copy(
                isFavorite = true,
                lastUpdated = System.currentTimeMillis()
            ))

            // Update in Firestore
            firestore.collection(LOCATIONS_COLLECTION)
                .document(location.placeId)
                .set(location.copy(isFavorite = true))
                .await()

            LocationResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location", e)
            LocationResult.Error(e)
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
            when (val result = locationSearchService.searchDogFriendlyLocations(
                radius = 1.0,  // Small radius since we're looking for a specific place
                filters = LocationSearchService.LocationFilters()
            ).firstOrNull()) {
                is LocationSearchService.SearchResult.Success -> {
                    val location = result.data.firstOrNull { it.placeId == placeId }
                        ?: throw Exception("Location not found")
                    val validatedLocation = validateLocation(location)
                    withContext(Dispatchers.IO) {
                        // Update cache
                        locationDao.insert(validatedLocation)
                        // Update Firestore
                        updateFirestoreLocation(validatedLocation)
                    }
                    LocationResult.Success(validatedLocation)
                }
                is LocationSearchService.SearchResult.Error ->
                    LocationResult.Error(result.exception)
                is LocationSearchService.SearchResult.Loading,
                null -> LocationResult.Error(Exception("No result found"))
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
            locationSearchService.searchDogFriendlyLocations(
                radius = radius,
                filters = LocationSearchService.LocationFilters(
                    venueTypes = setOf(venueType)
                )
            ).collect { result ->
                when (result) {
                    is LocationSearchService.SearchResult.Success -> {
                        val validatedLocations = validateAndFilterLocations(result.data)
                        withContext(Dispatchers.IO) {
                            locationDao.insertAll(validatedLocations)
                        }
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
            locationSearchService.searchDogFriendlyLocations(
                radius = radius,
                filters = LocationSearchService.LocationFilters(
                    outdoorOnly = true,
                    waterAvailable = amenities.contains(DogFriendlyLocation.Amenity.WATER_FOUNTAIN),
                    hasParking = amenities.contains(DogFriendlyLocation.Amenity.PARKING)
                )
            ).collect { result ->
                when (result) {
                    is LocationSearchService.SearchResult.Success -> {
                        val validatedLocations = validateAndFilterLocations(result.data)
                            .filter { location ->
                                amenities.all { amenity -> hasAmenity(location, amenity) }
                            }
                        withContext(Dispatchers.IO) {
                            locationDao.insertAll(validatedLocations)
                        }
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
    private fun hasAmenity(location: DogFriendlyLocation, amenity: DogFriendlyLocation.Amenity): Boolean {
        return when (amenity) {
            DogFriendlyLocation.Amenity.WATER_FOUNTAIN -> location.hasWaterFountain
            DogFriendlyLocation.Amenity.WASTE_STATIONS -> location.hasWasteStations
            DogFriendlyLocation.Amenity.PARKING -> location.hasParking
            DogFriendlyLocation.Amenity.SEATING -> location.hasSeating
            DogFriendlyLocation.Amenity.SHADE -> !location.isIndoor
            DogFriendlyLocation.Amenity.LIGHTING -> location.lightingAvailable
            DogFriendlyLocation.Amenity.AGILITY_EQUIPMENT -> location.amenities.contains("agility_equipment")
            DogFriendlyLocation.Amenity.SEPARATE_SMALL_DOG_AREA -> location.amenities.contains("small_dog_area")
            DogFriendlyLocation.Amenity.WASHING_STATION -> location.amenities.contains("washing_station")
            DogFriendlyLocation.Amenity.TREATS_AVAILABLE -> location.dogTreats
            DogFriendlyLocation.Amenity.DOG_MENU -> location.dogMenu
            DogFriendlyLocation.Amenity.WATER_BOWLS -> location.amenities.contains("water_bowls")
        }
    }
    // Helper extension function for calculateDistance
    fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
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
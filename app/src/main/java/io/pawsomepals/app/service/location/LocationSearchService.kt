package io.pawsomepals.app.service.location

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.DogFriendlyLocation.VenueType
import io.pawsomepals.app.utils.RemoteConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LocationSearchService @Inject constructor(
    private val placesClient: PlacesClient,
    private val remoteConfigManager: RemoteConfigManager
) {
    companion object {
        private const val TAG = "LocationSearchService"
        private const val DEFAULT_RADIUS = 5000.0 // 5km in meters
    }


    // Add these helper methods to LocationSearchService
    private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }
    private fun checkLocationShade(location: DogFriendlyLocation): Boolean {
        return location.placeTypes.any { type ->
            type in listOf(
                Place.Type.PARK.toString(),
                Place.Type.NATURAL_FEATURE.toString()
            )
        }
    }

    private fun checkLocationLighting(location: DogFriendlyLocation): Boolean {
        return location.placeTypes.any { type ->
            type in listOf(
                Place.Type.PARK.toString(),
                Place.Type.RESTAURANT.toString(),
                Place.Type.CAFE.toString(),
                Place.Type.BAR.toString()
            )
        }
    }

    private fun inferShadeAvailability(place: Place): Boolean {
        return place.types?.any { it == Place.Type.PARK || it == Place.Type.NATURAL_FEATURE } ?: false
    }

    private fun inferLightingAvailability(place: Place): Boolean {
        return place.types?.any {
            it == Place.Type.PARK ||
                    it == Place.Type.RESTAURANT ||
                    it == Place.Type.CAFE ||
                    it == Place.Type.BAR
        } ?: false
    }




    sealed class SearchResult<out T> {
        data class Success<T>(val data: T) : SearchResult<T>()
        data class Error(val exception: Exception) : SearchResult<Nothing>()
        object Loading : SearchResult<Nothing>()
    }

    init {
        Log.d(TAG, "Initializing LocationSearchService")
        checkPlacesInitialization()
    }

    private fun checkPlacesInitialization() {
        try {
            if (!Places.isInitialized()) {
                Log.e(TAG, "Places API not initialized!")
                val apiKey = remoteConfigManager.getMapsKey()
                Log.d(TAG, "Got API key from Remote Config: ${apiKey.take(5)}...")
            } else {
                Log.d(TAG, "Places API is already initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Places initialization", e)
        }
    }

    suspend fun searchNearbyLocations(
        location: LatLng,
        radius: Double = DEFAULT_RADIUS,
        filters: LocationFilters = LocationFilters()
    ): Flow<SearchResult<List<DogFriendlyLocation>>> = flow {
        try {
            emit(SearchResult.Loading)

            val placeFields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.TYPES,
                Place.Field.PHOTO_METADATAS,
                Place.Field.OPENING_HOURS,
                Place.Field.WEBSITE_URI
            )

            val bounds = createBoundsFromLocation(location, radius)
            val request = FindAutocompletePredictionsRequest.builder()
                .setLocationBias(RectangularBounds.newInstance(bounds))
                .setTypesFilter(getPlaceTypesForFilters(filters))
                .build()

            val predictions = withContext(Dispatchers.IO) {
                placesClient.findAutocompletePredictions(request).await()
            }

            val places = predictions.autocompletePredictions.mapNotNull { prediction ->
                try {
                    val placeRequest =
                        FetchPlaceRequest.builder(prediction.placeId, placeFields).build()
                    val place = withContext(Dispatchers.IO) {
                        placesClient.fetchPlace(placeRequest).await().place
                    }
                    DogFriendlyLocation.fromPlace(place)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching place details: ${e.message}")
                    null
                }
            }.filter { location -> applyFilters(location, filters) }

            emit(SearchResult.Success(places))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching nearby locations", e)
            emit(SearchResult.Error(e))
        }
    }

    suspend fun getAutocompleteResults(
        query: String,
        location: LatLng,
        radius: Double = DEFAULT_RADIUS
    ): SearchResult<List<String>> {
        return try {
            Log.d(TAG, "Getting autocomplete predictions for query: $query")
            val bounds = createBoundsFromLocation(location, radius)

            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setLocationBias(RectangularBounds.newInstance(bounds))
                .setTypesFilter(
                    listOf(
                        Place.Type.PARK.name,
                        Place.Type.RESTAURANT.name,
                        Place.Type.BAR.name,
                        Place.Type.CAFE.name
                    )
                )
                .build()

            val response = withContext(Dispatchers.IO) {
                placesClient.findAutocompletePredictions(request).await()
            }

            SearchResult.Success(
                response.autocompletePredictions.map {
                    it.getFullText(null).toString()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting autocomplete results", e)
            SearchResult.Error(e)
        }
    }

    private fun createBoundsFromLocation(center: LatLng, radiusInMeters: Double): LatLngBounds {
        val latRadian = Math.toRadians(center.latitude)
        val degLatKm = 110.574235
        val degLongKm = 110.572833 * Math.cos(latRadian)
        val deltaLat = radiusInMeters / 1000.0 / degLatKm
        val deltaLong = radiusInMeters / 1000.0 / degLongKm

        return LatLngBounds(
            LatLng(center.latitude - deltaLat, center.longitude - deltaLong),
            LatLng(center.latitude + deltaLat, center.longitude + deltaLong)
        )
    }

    private fun getPlaceTypesForFilters(filters: LocationFilters): List<String> {
        val types = mutableSetOf<String>()

        if (filters.venueTypes.isEmpty()) {
            types.addAll(listOf(
                Place.Type.PARK.toString(),
                Place.Type.RESTAURANT.toString(),
                Place.Type.BAR.toString(),
                Place.Type.CAFE.toString(),
                Place.Type.PET_STORE.toString(),
                Place.Type.VETERINARY_CARE.toString(),
                Place.Type.NATURAL_FEATURE.toString(),
                Place.Type.CAMPGROUND.toString(),
                Place.Type.RV_PARK.toString(),
                Place.Type.TOURIST_ATTRACTION.toString()
            ))
        } else {
            filters.venueTypes.forEach { venueType ->
                when (venueType) {
                    VenueType.DOG_PARK, VenueType.PUBLIC_PARK -> {
                        types.add(Place.Type.PARK.toString())
                        types.add(Place.Type.TOURIST_ATTRACTION.toString())
                    }
                    VenueType.RESTAURANT -> types.add(Place.Type.RESTAURANT.toString())
                    VenueType.BAR, VenueType.BREWERY -> types.add(Place.Type.BAR.toString())
                    VenueType.CAFE -> types.add(Place.Type.CAFE.toString())
                    VenueType.PET_STORE -> {
                        types.add(Place.Type.PET_STORE.toString())
                        types.add(Place.Type.STORE.toString())
                    }
                    VenueType.VET_CLINIC -> types.add(Place.Type.VETERINARY_CARE.toString())
                    VenueType.DOG_DAYCARE -> types.add(Place.Type.PET_STORE.toString())
                    VenueType.HIKING_TRAIL -> {
                        types.add(Place.Type.PARK.toString())
                        types.add(Place.Type.NATURAL_FEATURE.toString())
                    }
                    VenueType.BEACH -> {
                        types.add(Place.Type.NATURAL_FEATURE.toString())
                        types.add(Place.Type.TOURIST_ATTRACTION.toString())
                    }
                    VenueType.CAMPING -> {
                        types.add(Place.Type.CAMPGROUND.toString())
                        types.add(Place.Type.PARK.toString())
                        types.add(Place.Type.RV_PARK.toString())
                    }
                }
            }
        }
        return types.toList()
    }
    private fun matchesCustomType(place: Place, customType: String): Boolean {
        val placeName = place.name ?: return false
        val placeTypes = place.types?.map { it.toString() } ?: emptyList()

        return when (customType) {
            "dog_daycare" -> {
                placeName.contains("daycare", ignoreCase = true) ||
                        placeName.contains("day care", ignoreCase = true) ||
                        placeTypes.any { it.contains("pet", ignoreCase = true) }
            }
            "hiking_trail" -> {
                placeTypes.contains(Place.Type.PARK.toString()) &&
                        (placeName.contains("trail", ignoreCase = true) ||
                                placeName.contains("hiking", ignoreCase = true) ||
                                placeTypes.contains(Place.Type.NATURAL_FEATURE.toString()))
            }
            "beach" -> {
                placeTypes.contains(Place.Type.NATURAL_FEATURE.toString()) &&
                        (placeName.contains("beach", ignoreCase = true) ||
                                placeName.contains("shore", ignoreCase = true) ||
                                placeName.contains("coast", ignoreCase = true))
            }
            else -> false
        }
    }
    suspend fun searchDogFriendlyLocations(
        location: LatLng,
        radius: Double = DEFAULT_RADIUS,
        filters: LocationFilters = LocationFilters()
    ): Flow<SearchResult<List<DogFriendlyLocation>>> = flow {
        try {
            emit(SearchResult.Loading)

            // Define place fields we want to retrieve
            val placeFields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.TYPES,
                Place.Field.USER_RATINGS_TOTAL,
                Place.Field.PHOTO_METADATAS,
                Place.Field.OPENING_HOURS,
                Place.Field.WEBSITE_URI,
                Place.Field.PHONE_NUMBER
            )

            // Create bounds for location search
            val bounds = createBoundsFromLocation(location, radius)

            // Build the search request with filters
            val request = FindAutocompletePredictionsRequest.builder()
                .setLocationBias(RectangularBounds.newInstance(bounds))
                .setTypesFilter(getPlaceTypesForFilters(filters))
                .build()

            // Get predictions first
            val predictions = withContext(Dispatchers.IO) {
                placesClient.findAutocompletePredictions(request).await()
            }

            // Then fetch full place details for each prediction
            val places = predictions.autocompletePredictions.mapNotNull { prediction ->
                try {
                    val placeRequest = FetchPlaceRequest.builder(prediction.placeId, placeFields).build()
                    val place = withContext(Dispatchers.IO) {
                        placesClient.fetchPlace(placeRequest).await().place
                    }
                    DogFriendlyLocation.fromPlace(place)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching place details: ${e.message}")
                    null
                }
            }.filter { location -> applyFilters(location, filters) }

            // Sort results by rating and distance
            val sortedPlaces = places.sortedWith(
                compareByDescending<DogFriendlyLocation> { it.rating }
                    .thenBy { calculateDistance(location, it.getLatLng()) }
            )

            emit(SearchResult.Success(sortedPlaces))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching dog-friendly locations", e)
            emit(SearchResult.Error(e))
        }
    }


    suspend fun getLocationDetails(placeId: String): SearchResult<DogFriendlyLocation> {
        return try {
            val placeFields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.TYPES,
                Place.Field.USER_RATINGS_TOTAL,
                Place.Field.PHOTO_METADATAS,
                Place.Field.OPENING_HOURS,
                Place.Field.WEBSITE_URI,
                Place.Field.PHONE_NUMBER
            )

            val request = FetchPlaceRequest.builder(placeId, placeFields).build()
            val response = withContext(Dispatchers.IO) {
                placesClient.fetchPlace(request).await()
            }

            val place = response.place
            val dogFriendlyLocation = DogFriendlyLocation.fromPlace(place).copy(
                // Add additional playdate-specific attributes
                isOffLeashAllowed = inferOffLeashStatus(place),
                hasFencing = inferFencingStatus(place),
                hasWaterFountain = inferWaterAvailability(place),
                hasWasteStations = true, // Default assumption for dog-friendly places
                hasParking = place.types?.contains(Place.Type.PARKING) ?: false,
                hasSeating = inferSeatingAvailability(place),
                isIndoor = !inferOutdoorStatus(place),
                servesFood = place.types?.any {
                    it in listOf(Place.Type.RESTAURANT, Place.Type.CAFE)
                } ?: false,
                hasOutdoorSeating = inferOutdoorSeating(place)
            )

            SearchResult.Success(dogFriendlyLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location details", e)
            SearchResult.Error(e)
        }
    }

    suspend fun searchByVenueType(
        location: LatLng,
        venueType: DogFriendlyLocation.VenueType,
        radius: Double
    ): Flow<SearchResult<List<DogFriendlyLocation>>> = flow {
        try {
            emit(SearchResult.Loading)

            val filters = LocationFilters(
                venueTypes = setOf(venueType),
                outdoorOnly = venueType in setOf(
                    VenueType.DOG_PARK,
                    VenueType.PUBLIC_PARK,
                    VenueType.HIKING_TRAIL,
                    VenueType.BEACH
                )
            )

            // Reuse searchDogFriendlyLocations with venue-specific filters
            searchDogFriendlyLocations(location, radius, filters).collect { result ->
                when (result) {
                    is SearchResult.Success -> {
                        // Additional venue-specific filtering
                        val filteredPlaces = result.data.filter { place ->
                            when (venueType) {
                                VenueType.DOG_PARK -> place.isDogPark
                                VenueType.RESTAURANT -> place.servesFood
                                VenueType.CAFE -> place.servesFood && !place.servesDrinks
                                VenueType.BAR, VenueType.BREWERY -> place.servesDrinks
                                else -> true
                            }
                        }
                        emit(SearchResult.Success(filteredPlaces))
                    }
                    is SearchResult.Error -> emit(result)
                    is SearchResult.Loading -> emit(result)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching by venue type", e)
            emit(SearchResult.Error(e))
        }
    }

    suspend fun searchByAmenities(
        location: LatLng,
        amenities: Set<DogFriendlyLocation.Amenity>,
        radius: Double
    ): Flow<SearchResult<List<DogFriendlyLocation>>> = flow {
        try {
            emit(SearchResult.Loading)

            val filters = LocationFilters(
                requiredAmenities = amenities,
                outdoorOnly = amenities.any { it in outdoorAmenities }
            )

            searchDogFriendlyLocations(location, radius, filters).collect { result ->
                when (result) {
                    is SearchResult.Success -> {
                        val filteredPlaces = result.data.filter { place ->
                            amenities.all { amenity ->
                                when (amenity) {
                                    DogFriendlyLocation.Amenity.WATER_FOUNTAIN -> place.hasWaterFountain
                                    DogFriendlyLocation.Amenity.WASTE_STATIONS -> place.hasWasteStations
                                    DogFriendlyLocation.Amenity.PARKING -> place.hasParking
                                    DogFriendlyLocation.Amenity.SEATING -> place.hasSeating
                                    DogFriendlyLocation.Amenity.SHADE -> checkLocationShade(place)
                                    DogFriendlyLocation.Amenity.LIGHTING -> checkLocationLighting(place)
                                    else -> true
                                }
                            }
                        }
                        emit(SearchResult.Success(filteredPlaces))
                    }
                    is SearchResult.Error -> emit(result)
                    is SearchResult.Loading -> emit(result)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching by amenities", e)
            emit(SearchResult.Error(e))
        }
    }

    // Helper methods
    private fun inferOffLeashStatus(place: Place): Boolean {
        return place.types?.contains(Place.Type.PARK) == true &&
                (place.name?.lowercase()?.contains("dog park") == true ||
                        place.name?.lowercase()?.contains("off leash") == true)
    }

    private fun inferFencingStatus(place: Place): Boolean {
        return place.types?.contains(Place.Type.PARK) == true &&
                place.name?.lowercase()?.contains("dog park") == true
    }

    private fun inferWaterAvailability(place: Place): Boolean {
        return place.types?.any {
            it in listOf(Place.Type.PARK, Place.Type.RESTAURANT, Place.Type.CAFE)
        } ?: false
    }

    private fun inferSeatingAvailability(place: Place): Boolean {
        return place.types?.any {
            it in listOf(
                Place.Type.RESTAURANT,
                Place.Type.CAFE,
                Place.Type.BAR,
                Place.Type.PARK
            )
        } ?: false
    }

    private fun inferOutdoorStatus(place: Place): Boolean {
        return place.types?.any {
            it in listOf(
                Place.Type.PARK,
                Place.Type.NATURAL_FEATURE,
                Place.Type.TOURIST_ATTRACTION
            )
        } ?: false
    }

    private fun inferOutdoorSeating(place: Place): Boolean {
        return place.types?.any {
            it in listOf(
                Place.Type.RESTAURANT,
                Place.Type.CAFE,
                Place.Type.BAR
            )
        } ?: false
    }

    private val outdoorAmenities = setOf(
        DogFriendlyLocation.Amenity.WATER_FOUNTAIN,
        DogFriendlyLocation.Amenity.WASTE_STATIONS,
        DogFriendlyLocation.Amenity.SHADE
    )



    private fun applyFilters(
        location: DogFriendlyLocation,
        filters: LocationFilters
    ): Boolean {
        // Check venue types
        val matchesVenueType = if (filters.venueTypes.isEmpty()) {
            true
        } else {
            filters.venueTypes.any { venueType ->
                when (venueType) {
                    VenueType.DOG_PARK -> location.isDogPark
                    VenueType.RESTAURANT -> location.servesFood
                    VenueType.BAR, VenueType.BREWERY -> location.servesDrinks
                    VenueType.CAFE -> location.placeTypes.any { it == Place.Type.CAFE.toString() }
                    VenueType.PUBLIC_PARK -> location.placeTypes.any { it == Place.Type.PARK.toString() }
                    VenueType.PET_STORE -> location.placeTypes.any { it == Place.Type.PET_STORE.toString() }
                    VenueType.VET_CLINIC -> location.placeTypes.any { it == Place.Type.VETERINARY_CARE.toString() }
                    VenueType.DOG_DAYCARE -> location.isDogDaycare()
                    VenueType.HIKING_TRAIL -> location.isHikingTrail()
                    VenueType.BEACH -> location.isBeach()
                    VenueType.CAMPING -> location.isCampground()
                }
            }
        }

        // Check required amenities
        val hasRequiredAmenities = if (filters.requiredAmenities.isEmpty()) {
            true
        } else {
            filters.requiredAmenities.all { amenity ->
                location.amenities.contains(amenity.toString())
            }
        }

        return matchesVenueType &&
                hasRequiredAmenities &&
                (!filters.outdoorOnly || location.hasOutdoorSeating) &&
                (!filters.offLeashOnly || location.isOffLeashAllowed) &&
                (!filters.verifiedOnly || location.isVerified) &&
                (filters.minRating == null || (location.rating ?: 0.0) >= filters.minRating)
    }
    private fun DogFriendlyLocation.isDogDaycare(): Boolean {
        return placeTypes.contains(Place.Type.PET_STORE.toString()) ||
                name.lowercase().contains("daycare") ||
                name.lowercase().contains("day care")
    }

    private fun DogFriendlyLocation.isHikingTrail(): Boolean {
        return (placeTypes.contains(Place.Type.PARK.toString()) ||
                placeTypes.contains(Place.Type.NATURAL_FEATURE.toString())) &&
                (name.lowercase().contains("trail") ||
                        name.lowercase().contains("hiking"))
    }

    private fun DogFriendlyLocation.isBeach(): Boolean {
        return placeTypes.contains(Place.Type.NATURAL_FEATURE.toString()) &&
                (name.lowercase().contains("beach") ||
                        name.lowercase().contains("shore") ||
                        name.lowercase().contains("coast"))
    }

    private fun DogFriendlyLocation.isCampground(): Boolean {
        return placeTypes.contains(Place.Type.CAMPGROUND.toString()) ||
                placeTypes.contains(Place.Type.RV_PARK.toString())
    }


    data class LocationFilters(
        val venueTypes: Set<VenueType> = emptySet(),
        val requiredAmenities: Set<DogFriendlyLocation.Amenity> = emptySet(),
        val outdoorOnly: Boolean = false,
        val offLeashOnly: Boolean = false,
        val verifiedOnly: Boolean = false,
        val minRating: Double? = null
    )
}
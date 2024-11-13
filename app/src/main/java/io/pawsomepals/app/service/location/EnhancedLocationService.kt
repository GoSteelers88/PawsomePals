package io.pawsomepals.app.service.location

import com.google.android.gms.maps.model.LatLng
import io.pawsomepals.app.data.model.DogFriendlyLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnhancedLocationService @Inject constructor(
    private val googleMapsService: GoogleMapsService,
    private val placeService: PlaceService,
    private val locationCache: LocationCache
) {
    sealed class LocationSearchState {
        object Initial : LocationSearchState()
        object Loading : LocationSearchState()
        data class Success(
            val nearbyLocations: List<DogFriendlyLocation>,
            val recommendedLocations: List<DogFriendlyLocation>
        ) : LocationSearchState()
        data class Error(val error: Exception) : LocationSearchState()
    }

    suspend fun searchLocationsForPlaydate(
        user1Location: LatLng,
        user2Location: LatLng,
        searchRadius: Double = 5000.0
    ): Flow<LocationSearchState> = flow {
        try {
            emit(LocationSearchState.Loading)

            // Calculate midpoint between users
            val midpoint = calculateMidpoint(user1Location, user2Location)

            // First check cache
            val cachedLocations = locationCache.getNearbyLocations(midpoint, searchRadius)
            if (cachedLocations.isNotEmpty()) {
                emit(LocationSearchState.Success(
                    nearbyLocations = cachedLocations,
                    recommendedLocations = filterRecommendedLocations(cachedLocations)
                ))
                return@flow
            }

            // If cache miss, use both services
            val googleMapsLocations = googleMapsService.searchNearbyDogFriendlyPlaces(midpoint, searchRadius)
                .collect { placesResult ->
                    when (placesResult) {
                        is GoogleMapsService.PlacesResult.Success -> {
                            val convertedLocations = placesResult.data.mapNotNull {
                                DogFriendlyLocation.fromPlace(it)
                            }

                            val placeLocations = placeService.searchNearbyDogFriendlyPlaces(midpoint, searchRadius)
                            val combinedLocations = convertedLocations + placeLocations

                            // Cache the results
                            locationCache.cacheLocations(combinedLocations, midpoint, searchRadius)

                            emit(LocationSearchState.Success(
                                nearbyLocations = combinedLocations,
                                recommendedLocations = filterRecommendedLocations(combinedLocations)
                            ))
                        }
                        is GoogleMapsService.PlacesResult.Error -> {
                            emit(LocationSearchState.Error(placesResult.exception))
                        }
                    }
                }

        } catch (e: Exception) {
            emit(LocationSearchState.Error(e))
        }
    }

    private fun filterRecommendedLocations(
        locations: List<DogFriendlyLocation>
    ): List<DogFriendlyLocation> {
        return locations.filter { location ->
            location.rating ?: 0.0 >= 4.0 &&
                    location.userRatingsTotal ?: 0 >= 50 &&
                    location.amenities.size >= 3
        }.sortedByDescending { it.rating }
    }

    private fun calculateMidpoint(location1: LatLng, location2: LatLng): LatLng {
        return LatLng(
            (location1.latitude + location2.latitude) / 2,
            (location1.longitude + location2.longitude) / 2
        )
    }
}
package io.pawsomepals.app.service.location

import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationMatchingEngine @Inject constructor(
    private val locationService: LocationService
) {
    companion object {
        private const val VERY_CLOSE_DISTANCE = 5.0  // 5km
        private const val CLOSE_DISTANCE = 10.0     // 10km
        private const val MEDIUM_DISTANCE = 20.0    // 20km
        private const val FAR_DISTANCE = 50.0       // 50km
    }

    data class LocationScore(
        val score: Double,
        val distance: Double?,
        val commonAreas: List<DogFriendlyLocation>
    )

    fun calculateLocationScore(dog1: Dog, dog2: Dog): LocationScore {
        val distance = calculateDistance(dog1, dog2)
        return LocationScore(
            score = calculateScoreFromDistance(distance),
            distance = distance,
            commonAreas = findCommonAreas(dog1, dog2)
        )
    }

    private fun calculateDistance(dog1: Dog, dog2: Dog): Double? {
        val lat1 = dog1.latitude
        val lon1 = dog1.longitude
        val lat2 = dog2.latitude
        val lon2 = dog2.longitude

        return if (lat1 != null && lon1 != null &&
            lat2 != null && lon2 != null) {
            locationService.calculateDistance(lat1, lon1, lat2, lon2).toDouble()
        } else null
    }

    private fun calculateScoreFromDistance(distance: Double?): Double {
        if (distance == null) return 0.0
        return when {
            distance <= VERY_CLOSE_DISTANCE -> 1.0
            distance <= CLOSE_DISTANCE -> 0.75
            distance <= MEDIUM_DISTANCE -> 0.5
            distance <= FAR_DISTANCE -> 0.25
            else -> 0.0
        }
    }

    fun findCommonAreas(dog1: Dog, dog2: Dog): List<DogFriendlyLocation> {
        val dog1Areas = dog1.frequentedAreas ?: emptyList()
        val dog2Areas = dog2.frequentedAreas ?: emptyList()

        return dog1Areas.filter { area1 ->
            dog2Areas.any { area2 ->
                isAreaMatch(area1, area2)
            }
        }
    }

    private fun isAreaMatch(area1: DogFriendlyLocation, area2: DogFriendlyLocation): Boolean {
        // If we have exact place IDs, use those
        if (area1.placeId == area2.placeId) return true

        // Otherwise check proximity and type match
        val distance = locationService.calculateDistance(
            area1.latitude,
            area1.longitude,
            area2.latitude,
            area2.longitude
        )

        val isSameType = !area1.placeTypes.intersect(area2.placeTypes).isEmpty()
        val isNearby = distance <= 0.5 // Within 500m

        return isSameType && isNearby
    }

    private fun getDistanceBand(distance: Double): DistanceBand =
        when {
            distance <= VERY_CLOSE_DISTANCE -> DistanceBand.VERY_CLOSE
            distance <= CLOSE_DISTANCE -> DistanceBand.CLOSE
            distance <= MEDIUM_DISTANCE -> DistanceBand.MEDIUM
            distance <= FAR_DISTANCE -> DistanceBand.FAR
            else -> DistanceBand.FAR
        }

    fun getDogsByDistance(
        latitude: Double,
        longitude: Double,
        radius: Double,
        dogs: List<Dog>
    ): Map<DistanceBand, List<Dog>> {
        return dogs
            .filter { dog ->
                dog.latitude != null && dog.longitude != null
            }
            .map { dog ->
                // We can safely use !! here because we filtered nulls above
                val distance = locationService.calculateDistance(
                    latitude,
                    longitude,
                    dog.latitude!!,
                    dog.longitude!!
                ).toDouble()
                Pair(dog, getDistanceBand(distance))
            }
            .groupBy(
                keySelector = { it.second },
                valueTransform = { it.first }
            )
    }
    suspend fun findDogsInRadius(
        latitude: Double,
        longitude: Double,
        radius: Double
    ): List<Dog> {
        // Implementation would go here
        // For now, return empty list as placeholder
        return emptyList()
    }

    enum class DistanceBand(val maxDistance: Double) {
        VERY_CLOSE(VERY_CLOSE_DISTANCE),
        CLOSE(CLOSE_DISTANCE),
        MEDIUM(MEDIUM_DISTANCE),
        FAR(FAR_DISTANCE);

        companion object {
            fun fromDistance(distance: Double): DistanceBand =
                values().first { distance <= it.maxDistance }
        }
    }

    fun isInRange(dog1: Dog, dog2: Dog, maxDistance: Double): Boolean {
        val distance = calculateDistance(dog1, dog2)
        return distance != null && distance <= maxDistance
    }

    fun findOptimalMeetingPoint(
        dog1: Dog,
        dog2: Dog,
        commonAreas: List<DogFriendlyLocation>
    ): DogFriendlyLocation? {
        if (commonAreas.isEmpty()) {
            return findMidPoint(dog1, dog2)
        }

        return commonAreas.minByOrNull { area ->
            val distanceToDoc1 = calculateDistanceToArea(dog1, area)
            val distanceToDoc2 = calculateDistanceToArea(dog2, area)
            distanceToDoc1 + distanceToDoc2
        }
    }

    private fun findMidPoint(dog1: Dog, dog2: Dog): DogFriendlyLocation? {
        val lat1 = dog1.latitude
        val lon1 = dog1.longitude
        val lat2 = dog2.latitude
        val lon2 = dog2.longitude

        if (lat1 == null || lon1 == null ||
            lat2 == null || lon2 == null) {
            return null
        }

        return DogFriendlyLocation(
            placeId = "",
            name = "Midpoint Location",
            address = "",
            latitude = (lat1 + lat2) / 2,
            longitude = (lon1 + lon2) / 2,
            placeTypes = emptyList(),
            rating = null,
            userRatingsTotal = null,
            phoneNumber = null,
            websiteUri = null
        )
    }

    private fun calculateDistanceToArea(
        dog: Dog,
        area: DogFriendlyLocation
    ): Double {
        val dogLat = dog.latitude
        val dogLon = dog.longitude

        if (dogLat == null || dogLon == null) {
            return Double.POSITIVE_INFINITY
        }

        return locationService.calculateDistance(
            dogLat,
            dogLon,
            area.latitude,
            area.longitude
        ).toDouble()
    }
}
package com.example.pawsomepals.data.model

import kotlin.math.*

data class SwipeLocation(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        private const val EARTH_RADIUS_KM = 6371.0 // Earth's radius in kilometers

        fun fromString(locationString: String?): SwipeLocation? {
            if (locationString == null) return null
            return try {
                val (lat, lon) = locationString.split(",").map { it.toDouble() }
                if (isValidLatitude(lat) && isValidLongitude(lon)) {
                    SwipeLocation(lat, lon)
                } else null
            } catch (e: Exception) {
                null
            }
        }

        private fun isValidLatitude(lat: Double): Boolean = lat in -90.0..90.0
        private fun isValidLongitude(lon: Double): Boolean = lon in -180.0..180.0
    }

    init {
        require(isValidLatitude(latitude)) { "Invalid latitude: $latitude" }
        require(isValidLongitude(longitude)) { "Invalid longitude: $longitude" }
    }

    fun toLocationString(): String = "$latitude,$longitude"

    // Calculate distance to another location in kilometers using Haversine formula
    fun distanceTo(other: SwipeLocation): Double {
        val lat1 = this.latitude.toRadians()
        val lon1 = this.longitude.toRadians()
        val lat2 = other.latitude.toRadians()
        val lon2 = other.longitude.toRadians()

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        return EARTH_RADIUS_KM * c
    }

    // Check if location is within radius (in km) of another location
    fun isWithinRadius(other: SwipeLocation, radiusKm: Double): Boolean {
        return distanceTo(other) <= radiusKm
    }

    private fun Double.toRadians(): Double = this * PI / 180.0

    override fun toString(): String = "($latitude, $longitude)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SwipeLocation) return false
        return latitude == other.latitude && longitude == other.longitude
    }

    override fun hashCode(): Int {
        var result = latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        return result
    }
}
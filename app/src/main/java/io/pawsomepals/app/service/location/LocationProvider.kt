package io.pawsomepals.app.service.location

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import io.pawsomepals.app.data.model.Dog

interface LocationProvider {
    suspend fun getCurrentLocation(): Location?
    suspend fun getLastKnownLocation(): Location?
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float
    suspend fun filterProfilesByDistance(profiles: List<Dog>, maxDistance: Double): List<Dog>
    suspend fun getDogLocation(dogId: String): LatLng?
}
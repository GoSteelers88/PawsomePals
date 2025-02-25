package io.pawsomepals.app.utils

import com.firebase.geofire.GeoFireUtils as FirebaseGeoFireUtils
import com.firebase.geofire.GeoLocation as FirebaseGeoLocation

object GeoFireUtils {
    fun getGeoHashForLocation(location: GeoLocation): String {
        return FirebaseGeoFireUtils.getGeoHashForLocation(
            FirebaseGeoLocation(location.latitude, location.longitude)
        )
    }

    fun getGeoHashQueryBounds(location: GeoLocation, radiusInMeters: Double): List<GeoQueryBounds> {
        val firebaseLocation = FirebaseGeoLocation(location.latitude, location.longitude)
        return FirebaseGeoFireUtils.getGeoHashQueryBounds(firebaseLocation, radiusInMeters)
            .map { GeoQueryBounds(it.startHash, it.endHash) }
    }

    fun getDistanceBetween(location1: GeoLocation, location2: GeoLocation): Double {
        val firebaseLocation1 = FirebaseGeoLocation(location1.latitude, location1.longitude)
        val firebaseLocation2 = FirebaseGeoLocation(location2.latitude, location2.longitude)
        return FirebaseGeoFireUtils.getDistanceBetween(firebaseLocation1, firebaseLocation2)
    }
}
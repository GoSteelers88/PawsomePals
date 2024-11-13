package io.pawsomepals.app.service.location

import android.content.Context
import android.util.LruCache
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.data.model.DogFriendlyLocation
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = LruCache<String, List<DogFriendlyLocation>>(50)
    private val cacheTimeout = TimeUnit.HOURS.toMillis(1)
    private val cacheTimestamps = mutableMapOf<String, Long>()

    fun getNearbyLocations(
        location: LatLng,
        radius: Double
    ): List<DogFriendlyLocation> {
        val cacheKey = "${location.latitude},${location.longitude}-$radius"
        val timestamp = cacheTimestamps[cacheKey]

        return if (timestamp != null &&
            System.currentTimeMillis() - timestamp < cacheTimeout
        ) {
            cache.get(cacheKey) ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun cacheLocations(
        locations: List<DogFriendlyLocation>,
        location: LatLng,
        radius: Double
    ) {
        val cacheKey = "${location.latitude},${location.longitude}-$radius"
        cache.put(cacheKey, locations)
        cacheTimestamps[cacheKey] = System.currentTimeMillis()
    }
}
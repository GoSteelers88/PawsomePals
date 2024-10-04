package com.example.pawsomepals.data.model

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class DogFriendlyLocation(
    val id: String,
    val name: String,
    val address: String,
    val latLng: @RawValue LatLng,
    val rating: Float?,
    val types: List<String>,
    val isVerified: Boolean = false,
    val amenities: List<Amenity> = emptyList()
) : Parcelable {

    enum class Amenity {
        WATER_FOUNTAIN,
        WASTE_BAGS,
        OFF_LEASH_AREA,
        SHADE,
        SEATING,
        PARKING,
        RESTROOMS
    }

    fun distanceTo(other: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            latLng.latitude, latLng.longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }

    companion object {
        fun fromPlace(place: com.google.android.libraries.places.api.model.Place): DogFriendlyLocation {
            return DogFriendlyLocation(
                id = place.id ?: "",
                name = place.name ?: "",
                address = place.address ?: "",
                latLng = place.latLng ?: LatLng(0.0, 0.0),
                rating = place.rating?.toFloat(),
                types = place.types?.map { it.name } ?: emptyList(),
                isVerified = false,
                amenities = emptyList()
            )
        }
    }
}
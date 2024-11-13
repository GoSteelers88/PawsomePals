package io.pawsomepals.app.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "dog_friendly_locations")
data class DogFriendlyLocation(
    @PrimaryKey
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val placeTypes: List<String>,
    val rating: Double?,
    val userRatingsTotal: Int?,
    val phoneNumber: String?,
    val websiteUri: String?,

    // Dog-specific attributes
    val isDogPark: Boolean = false,
    val isOffLeashAllowed: Boolean = false,
    val hasFencing: Boolean = false,
    val hasWaterFountain: Boolean = false,
    val hasWasteStations: Boolean = false,
    val hasParking: Boolean = false,
    val hasSeating: Boolean = false,
    val isIndoor: Boolean = false,

    // Venue-specific attributes
    val servesFood: Boolean = false,
    val servesDrinks: Boolean = false,
    val hasOutdoorSeating: Boolean = false,

    // Verification and community data
    val isVerified: Boolean = false,
    val communityRating: Double? = null,
    val dogFriendlyRating: Double? = null,
    val reviewCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),

    // Operating information
    val openNow: Boolean? = null,
    val priceLevel: Int? = null,

    // Photos
    val photoReferences: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val restrictions: List<String> = emptyList(),
    val notes: String? = null,



) : Parcelable {

    fun getLatLng(): LatLng = LatLng(latitude, longitude)

    companion object {
        fun fromPlace(place: Place): DogFriendlyLocation {
            return DogFriendlyLocation(
                placeId = place.id!!,
                name = place.name!!,
                address = place.address ?: "",
                latitude = place.latLng?.latitude ?: 0.0,
                longitude = place.latLng?.longitude ?: 0.0,
                placeTypes = place.types?.map { it.name } ?: emptyList(),
                rating = place.rating,
                userRatingsTotal = place.userRatingsTotal,
                phoneNumber = place.phoneNumber,
                websiteUri = place.websiteUri?.toString(),
                openNow = place.isOpen,
                priceLevel = place.priceLevel,

                // Infer dog-friendly attributes based on place type
                isDogPark = place.types?.contains(Place.Type.PARK) == true,
                isOffLeashAllowed = false, // Needs to be verified
                hasFencing = false, // Needs to be verified
                hasWaterFountain = false, // Needs to be verified
                hasWasteStations = false, // Needs to be verified
                hasParking = false, // Needs to be verified
                hasSeating = false, // Needs to be verified
                isIndoor = false, // Needs to be verified

                // Infer venue attributes based on place type
                servesFood = place.types?.contains(Place.Type.RESTAURANT) == true ||
                        place.types?.contains(Place.Type.CAFE) == true,
                servesDrinks = place.types?.contains(Place.Type.BAR) == true ||
                        place.types?.contains(Place.Type.CAFE) == true,
                hasOutdoorSeating = false, // Needs to be verified

                // Photos
                photoReferences = place.photoMetadatas?.map { it.attributions.toString() } ?: emptyList()
            )
        }
    }

    enum class Amenity {
        WATER_FOUNTAIN,
        WASTE_STATIONS,
        PARKING,
        SEATING,
        SHADE,
        LIGHTING,
        AGILITY_EQUIPMENT,
        SEPARATE_SMALL_DOG_AREA,
        WASHING_STATION,
        TREATS_AVAILABLE
    }

    enum class Restriction {
        LEASH_REQUIRED,
        BREED_RESTRICTIONS,
        SIZE_RESTRICTIONS,
        VACCINATION_REQUIRED,
        TIME_RESTRICTIONS,
        MEMBERSHIP_REQUIRED
    }

    enum class VenueType {
        DOG_PARK,
        PUBLIC_PARK,
        RESTAURANT,
        BAR,
        BREWERY,
        CAFE,
        PET_STORE,
        VET_CLINIC,
        DOG_DAYCARE,
        HIKING_TRAIL,
        BEACH,
        CAMPING
    }
    @ColumnInfo(name = "distance")

    var distance: Double? = null

}
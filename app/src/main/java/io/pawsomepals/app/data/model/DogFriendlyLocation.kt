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
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "distanceMeters")
    val distanceMeters: Double? = null, // Nullable, set dynamically
    val businessStatus: String? = null,
    val verificationDate: Long? = null,
    val dogSize: List<String> = emptyList(),
    val breedRestrictions: List<String> = emptyList(),
    val dogMenu: Boolean = false,
    val dogTreats: Boolean = false,

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
    val maxConcurrentDogs: Int = 0,
    val playdateHours: String? = null,
    val surfaceType: String? = null,  // grass, sand, gravel, etc.
    val shadeAvailable: Boolean = false,
    val lightingAvailable: Boolean = false,
    val hasSecureEntrance: Boolean = false,  // Double-gate system
    val weatherProtection: Boolean = false,  // Covered areas for rain/sun
    val quietSpaceAvailable: Boolean = false,  // Areas for dogs to decompress

    // Additional safety features
    val emergencyVetNearby: Boolean = false,
    val firstAidAvailable: Boolean = false,

    // Enhanced venue attributes
    val noiseLevel: String? = null,  // quiet, moderate, loud
    val crowdLevel: String? = null,  // low, medium, high
    val bestTimeForPlaydate: String? = null,

    // Operating information
    val openNow: Boolean? = null,
    val priceLevel: Int? = null,

    // Photos
    val photoReferences: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val restrictions: List<String> = emptyList(),
    val notes: String? = null,
    val isPlaydateEnabled: Boolean = false,
    val distanceFromMidpoint: Float = 0f,

    // New field from LocationSearchService
    val isDogFriendlyFromReviews: Boolean = false // Persist review-based dog-friendliness
) : Parcelable {

    fun getLatLng(): LatLng = LatLng(latitude, longitude)

    companion object {
        fun fromPlace(place: Place, distanceMeters: Double? = null): DogFriendlyLocation {
            val types = place.types?.map { it.toString().lowercase() } ?: emptyList()
            val nameLower = place.name?.lowercase() ?: ""

            // Define venue types explicitly
            val parkTypes = listOf(
                Place.Type.PARK,
                Place.Type.PET_STORE,  // Used for dog parks
                Place.Type.NATURAL_FEATURE
            )
            val foodTypes = listOf(
                Place.Type.BAR,
                Place.Type.RESTAURANT,
                Place.Type.CAFE
            )

            // Enhance type detection for playdate venues
            val isDogPlaydateVenue = place.types?.any { type ->
                type in parkTypes || (type in foodTypes &&
                        containsAnyInString(nameLower, "dog", "pup", "bark", "pet"))
            } ?: false

            val isPark = place.types?.contains(Place.Type.PARK) ?: false
            val isPetStore = place.types?.contains(Place.Type.PET_STORE) ?: false

            return DogFriendlyLocation(
                placeId = place.id ?: "",
                name = place.name ?: "Unknown",
                address = place.address ?: "",
                latitude = place.latLng?.latitude ?: 0.0,
                longitude = place.latLng?.longitude ?: 0.0,
                placeTypes = types,
                rating = place.rating,
                userRatingsTotal = place.userRatingsTotal,
                phoneNumber = place.phoneNumber,
                websiteUri = place.websiteUri?.toString(),
                distanceMeters = distanceMeters,
                openNow = place.isOpen,
                priceLevel = place.priceLevel,
                isPlaydateEnabled = isDogPlaydateVenue,
                distanceFromMidpoint = 0f,

                // Enhanced inference from Place types and name
                isDogPark = isPark && containsAnyInString(nameLower, "dog", "bark", "pup") || isPetStore,
                isOffLeashAllowed = isPark && (
                        nameLower.contains("off leash") || nameLower.contains("dog park")
                        ),
                hasFencing = isPark && (
                        nameLower.contains("dog park") || nameLower.contains("fenced")
                        ),
                hasWaterFountain = place.types?.any { type ->
                    type in listOf(Place.Type.PARK, Place.Type.RESTAURANT, Place.Type.CAFE)
                } ?: false,
                hasWasteStations = place.types?.any { type ->
                    type in listOf(Place.Type.PARK, Place.Type.PET_STORE)
                } ?: false,
                hasParking = place.types?.any { type ->
                    type in listOf(Place.Type.PARKING, Place.Type.PARK)
                } ?: false,
                hasSeating = place.types?.any { type ->
                    type in listOf(
                        Place.Type.RESTAURANT,
                        Place.Type.CAFE,
                        Place.Type.BAR,
                        Place.Type.PARK
                    )
                } ?: false,
                isIndoor = place.types?.none { type ->
                    type in listOf(
                        Place.Type.PARK,
                        Place.Type.NATURAL_FEATURE
                    )
                } ?: true,

                // Playdate-specific defaults
                maxConcurrentDogs = if (isPetStore || isPark) 20 else 5,
                playdateHours = place.openingHours?.periods?.firstOrNull()?.toString(),
                surfaceType = when {
                    place.types?.contains(Place.Type.NATURAL_FEATURE) == true -> "sand"
                    isPark -> "grass"
                    else -> null
                },
                shadeAvailable = isPark,
                lightingAvailable = place.types?.any { type ->
                    type in listOf(
                        Place.Type.PARK,
                        Place.Type.RESTAURANT,
                        Place.Type.CAFE,
                        Place.Type.BAR
                    )
                } ?: false,
                hasSecureEntrance = isPetStore,
                weatherProtection = place.types?.none { type ->
                    type in listOf(
                        Place.Type.PARK,
                        Place.Type.NATURAL_FEATURE
                    )
                } ?: false,
                quietSpaceAvailable = isPetStore || isPark,

                // Safety defaults
                emergencyVetNearby = false,
                firstAidAvailable = isPetStore,

                // Venue attributes
                noiseLevel = when {
                    place.types?.any { it in listOf(Place.Type.BAR, Place.Type.RESTAURANT) } == true -> "moderate"
                    place.types?.any { it in listOf(Place.Type.PARK, Place.Type.PET_STORE) } == true -> "moderate"
                    place.types?.contains(Place.Type.CAFE) == true -> "quiet"
                    else -> null
                },
                crowdLevel = when {
                    place.types?.any { it in listOf(Place.Type.BAR, Place.Type.RESTAURANT, Place.Type.PARK) } == true -> "medium"
                    place.types?.any { it in listOf(Place.Type.CAFE, Place.Type.PET_STORE) } == true -> "low"
                    else -> null
                },
                bestTimeForPlaydate = when {
                    place.types?.any { it in listOf(Place.Type.PARK, Place.Type.PET_STORE) } == true -> "morning and evening"
                    place.types?.any { it in listOf(Place.Type.BAR) } == true -> "afternoon"
                    place.types?.contains(Place.Type.CAFE) == true -> "morning"
                    else -> null
                }
            )
        }

        private fun containsAnyInString(str: String, vararg values: String): Boolean {
            return values.any { str.contains(it) }
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
        TREATS_AVAILABLE,
        DOG_MENU,
        WATER_BOWLS
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
        CAMPING,
        DOG_BOARDING,
        GROOMING,
        DOG_TRAINING,
        BOTANICAL_GARDEN,  // Added
        AGILITY_CENTER
    }
}
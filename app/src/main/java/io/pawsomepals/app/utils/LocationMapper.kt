package io.pawsomepals.app.utils

import android.location.Location
import io.pawsomepals.app.data.model.DogFriendlyLocation
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationMapper @Inject constructor() {

    fun mapPlaceToDogFriendlyLocation(place: Place): DogFriendlyLocation {
        val venueType = determineVenueType(place)
        val (isDogPark, isOffLeash) = inferDogFriendlyAttributes(venueType, place.types)
        val inferredAmenities = inferAmenities(place)

        return DogFriendlyLocation(
            placeId = place.id ?: "",
            name = place.name ?: "",
            address = place.address ?: "",
            latitude = place.latLng?.latitude ?: 0.0,
            longitude = place.latLng?.longitude ?: 0.0,
            placeTypes = place.types?.map { it.toString() } ?: emptyList(),
            rating = place.rating,
            userRatingsTotal = place.userRatingsTotal,
            phoneNumber = place.phoneNumber,
            websiteUri = place.websiteUri?.toString(),

            isDogPark = isDogPark,
            isOffLeashAllowed = isOffLeash,
            hasFencing = inferredAmenities.contains(DogFriendlyLocation.Amenity.SEPARATE_SMALL_DOG_AREA),
            hasWaterFountain = inferredAmenities.contains(DogFriendlyLocation.Amenity.WATER_FOUNTAIN),
            hasWasteStations = inferredAmenities.contains(DogFriendlyLocation.Amenity.WASTE_STATIONS),
            hasParking = place.types?.contains(Place.Type.PARKING) == true,
            hasSeating = inferredAmenities.contains(DogFriendlyLocation.Amenity.SEATING),
            isIndoor = isLikelyIndoor(place.types),

            servesFood = place.types?.contains(Place.Type.RESTAURANT) == true ||
                    place.types?.contains(Place.Type.CAFE) == true,
            servesDrinks = place.types?.contains(Place.Type.BAR) == true ||
                    place.types?.contains(Place.Type.CAFE) == true,
            hasOutdoorSeating = inferHasOutdoorSeating(place),

            photoReferences = place.photoMetadatas?.map { it.attributions.toString() } ?: emptyList(),
            amenities = inferredAmenities.map { it.name },

            openNow = place.isOpen == true,
            priceLevel = place.priceLevel,

            isVerified = false,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun determineVenueType(place: Place): DogFriendlyLocation.VenueType {
        return when {
            place.types?.contains(Place.Type.PARK) == true -> {
                if (place.name?.lowercase()?.contains("dog") == true) {
                    DogFriendlyLocation.VenueType.DOG_PARK
                } else {
                    DogFriendlyLocation.VenueType.PUBLIC_PARK
                }
            }
            place.types?.contains(Place.Type.BAR) == true -> {
                if (place.name?.lowercase()?.contains("brew") == true) {
                    DogFriendlyLocation.VenueType.BREWERY
                } else {
                    DogFriendlyLocation.VenueType.BAR
                }
            }
            place.types?.contains(Place.Type.RESTAURANT) == true -> DogFriendlyLocation.VenueType.RESTAURANT
            place.types?.contains(Place.Type.CAFE) == true -> DogFriendlyLocation.VenueType.CAFE
            place.types?.contains(Place.Type.PET_STORE) == true -> DogFriendlyLocation.VenueType.PET_STORE
            place.types?.contains(Place.Type.VETERINARY_CARE) == true -> DogFriendlyLocation.VenueType.VET_CLINIC
            place.types?.contains(Place.Type.CAMPGROUND) == true -> DogFriendlyLocation.VenueType.CAMPING
            place.types?.any { it.toString().contains("beach", ignoreCase = true) } == true ->
                DogFriendlyLocation.VenueType.BEACH
            else -> determineVenueTypeFromName(place.name)
        }
    }

    private fun determineVenueTypeFromName(name: String?): DogFriendlyLocation.VenueType {
        return when {
            name?.lowercase()?.contains("dog park") == true -> DogFriendlyLocation.VenueType.DOG_PARK
            name?.lowercase()?.contains("brewery") == true -> DogFriendlyLocation.VenueType.BREWERY
            name?.lowercase()?.contains("trail") == true -> DogFriendlyLocation.VenueType.HIKING_TRAIL
            name?.lowercase()?.contains("daycare") == true -> DogFriendlyLocation.VenueType.DOG_DAYCARE
            else -> DogFriendlyLocation.VenueType.PUBLIC_PARK
        }
    }

    private fun inferDogFriendlyAttributes(
        venueType: DogFriendlyLocation.VenueType,
        placeTypes: List<Place.Type>?
    ): Pair<Boolean, Boolean> {
        val isDogPark = venueType == DogFriendlyLocation.VenueType.DOG_PARK
        val isOffLeash = when (venueType) {
            DogFriendlyLocation.VenueType.DOG_PARK -> true
            DogFriendlyLocation.VenueType.PUBLIC_PARK -> false
            else -> false
        }
        return Pair(isDogPark, isOffLeash)
    }

    private fun inferAmenities(place: Place): Set<DogFriendlyLocation.Amenity> {
        val amenities = mutableSetOf<DogFriendlyLocation.Amenity>()

        when {
            place.types?.contains(Place.Type.PARK) == true -> {
                amenities.add(DogFriendlyLocation.Amenity.WATER_FOUNTAIN)
                amenities.add(DogFriendlyLocation.Amenity.WASTE_STATIONS)
                amenities.add(DogFriendlyLocation.Amenity.SEATING)
            }
            place.types?.contains(Place.Type.RESTAURANT) == true ||
                    place.types?.contains(Place.Type.CAFE) == true ||
                    place.types?.contains(Place.Type.BAR) == true -> {
                amenities.add(DogFriendlyLocation.Amenity.WATER_FOUNTAIN)
                amenities.add(DogFriendlyLocation.Amenity.SEATING)
            }
        }

        place.name?.lowercase()?.let { name ->
            if (name.contains("dog park")) {
                amenities.add(DogFriendlyLocation.Amenity.SEPARATE_SMALL_DOG_AREA)
                amenities.add(DogFriendlyLocation.Amenity.WASTE_STATIONS)
                amenities.add(DogFriendlyLocation.Amenity.WATER_FOUNTAIN)
            }
            if (name.contains("pet store") || name.contains("pet shop")) {
                amenities.add(DogFriendlyLocation.Amenity.TREATS_AVAILABLE)
            }
        }

        return amenities
    }

    private fun inferHasOutdoorSeating(place: Place): Boolean {
        return place.types?.any { type ->
            type == Place.Type.RESTAURANT ||
                    type == Place.Type.CAFE ||
                    type == Place.Type.BAR
        } == true
    }

    private fun isLikelyIndoor(placeTypes: List<Place.Type>?): Boolean {
        return placeTypes?.any { type ->
            type == Place.Type.RESTAURANT ||
                    type == Place.Type.SHOPPING_MALL ||
                    type == Place.Type.PET_STORE ||
                    type == Place.Type.STORE ||
                    type == Place.Type.VETERINARY_CARE
        } == true
    }

    fun latLngToLocation(latLng: LatLng): Location {
        return Location("").apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
        }
    }

    fun locationToLatLng(location: Location): LatLng {
        return LatLng(location.latitude, location.longitude)
    }

    fun calculateDistance(location1: LatLng, location2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location1.latitude,
            location1.longitude,
            location2.latitude,
            location2.longitude,
            results
        )
        return results[0]
    }
}
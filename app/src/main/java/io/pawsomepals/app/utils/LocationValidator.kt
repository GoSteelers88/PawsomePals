package io.pawsomepals.app.utils

import android.util.Log
import io.pawsomepals.app.data.model.DogFriendlyLocation
import com.google.android.gms.maps.model.LatLng
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationValidator @Inject constructor() {

    companion object {
        private const val TAG = "LocationValidator"
        private const val MIN_RATING = 3.0
        private const val MIN_RATINGS_COUNT = 5
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_ADDRESS_LENGTH = 200
        private val VALID_LATITUDE_RANGE = -90.0..90.0
        private val VALID_LONGITUDE_RANGE = -180.0..180.0
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError> = emptyList(),
        val warnings: List<ValidationWarning> = emptyList()
    )

    sealed class ValidationError {
        object InvalidCoordinates : ValidationError()
        object MissingRequiredField : ValidationError()
        object InvalidWebsiteUrl : ValidationError()
        object InvalidPhoneNumber : ValidationError()
        object InvalidPlaceId : ValidationError()
        data class CustomError(val message: String) : ValidationError()
    }

    sealed class ValidationWarning {
        object LowRating : ValidationWarning()
        object FewReviews : ValidationWarning()
        object MissingPhotos : ValidationWarning()
        object UnverifiedLocation : ValidationWarning()
        object IncompleteAmenities : ValidationWarning()
        data class CustomWarning(val message: String) : ValidationWarning()
    }

    fun validateLocation(location: DogFriendlyLocation): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Validate required fields
        validateRequiredFields(location, errors)

        // Validate coordinates
        validateCoordinates(location, errors)

        // Validate URLs and phone numbers
        validateContactInfo(location, errors)

        // Validate ratings and reviews
        validateRatings(location, warnings)

        // Validate venue-specific requirements
        validateVenueSpecificRequirements(location, errors, warnings)

        // Validate amenities
        validateAmenities(location, warnings)

        // Check verification status
        if (!location.isVerified) {
            warnings.add(ValidationWarning.UnverifiedLocation)
        }

        // Validate photos
        if (location.photoReferences.isEmpty()) {
            warnings.add(ValidationWarning.MissingPhotos)
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun validateRequiredFields(
        location: DogFriendlyLocation,
        errors: MutableList<ValidationError>
    ) {
        if (location.placeId.isBlank() ||
            location.name.isBlank() ||
            location.address.isBlank()
        ) {
            errors.add(ValidationError.MissingRequiredField)
        }

        if (location.name.length > MAX_NAME_LENGTH ||
            location.address.length > MAX_ADDRESS_LENGTH
        ) {
            errors.add(ValidationError.CustomError("Name or address exceeds maximum length"))
        }
    }

    private fun validateCoordinates(
        location: DogFriendlyLocation,
        errors: MutableList<ValidationError>
    ) {
        if (!isValidLatLng(LatLng(location.latitude, location.longitude))) {
            errors.add(ValidationError.InvalidCoordinates)
        }
    }

    private fun validateContactInfo(
        location: DogFriendlyLocation,
        errors: MutableList<ValidationError>
    ) {
        location.websiteUri?.let {
            if (!isValidUrl(it)) {
                errors.add(ValidationError.InvalidWebsiteUrl)
            }
        }

        location.phoneNumber?.let {
            if (!isValidPhoneNumber(it)) {
                errors.add(ValidationError.InvalidPhoneNumber)
            }
        }
    }

    private fun validateRatings(
        location: DogFriendlyLocation,
        warnings: MutableList<ValidationWarning>
    ) {
        location.rating?.let {
            if (it < MIN_RATING) {
                warnings.add(ValidationWarning.LowRating)
            }
        }

        location.userRatingsTotal?.let {
            if (it < MIN_RATINGS_COUNT) {
                warnings.add(ValidationWarning.FewReviews)
            }
        }
    }

    private fun validateVenueSpecificRequirements(
        location: DogFriendlyLocation,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        when (location.placeTypes.firstOrNull()) {
            DogFriendlyLocation.VenueType.DOG_PARK.name -> {
                if (!location.hasWasteStations || !location.hasWaterFountain) {
                    warnings.add(ValidationWarning.CustomWarning("Dog park missing essential amenities"))
                }
            }
            DogFriendlyLocation.VenueType.RESTAURANT.name,
            DogFriendlyLocation.VenueType.BAR.name,
            DogFriendlyLocation.VenueType.BREWERY.name -> {
                if (!location.hasOutdoorSeating) {
                    warnings.add(ValidationWarning.CustomWarning("Venue might not be suitable for dogs without outdoor seating"))
                }
            }
            DogFriendlyLocation.VenueType.HIKING_TRAIL.name -> {
                if (!location.hasParking) {
                    warnings.add(ValidationWarning.CustomWarning("Trail missing parking information"))
                }
            }
        }
    }

    private fun validateAmenities(
        location: DogFriendlyLocation,
        warnings: MutableList<ValidationWarning>
    ) {
        if (location.amenities.isEmpty()) {
            warnings.add(ValidationWarning.IncompleteAmenities)
        }
    }

    fun isValidLatLng(latLng: LatLng): Boolean {
        return latLng.latitude in VALID_LATITUDE_RANGE &&
                latLng.longitude in VALID_LONGITUDE_RANGE
    }

    fun isValidUrl(urlString: String): Boolean {
        return try {
            URL(urlString).toURI()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Invalid URL: $urlString", e)
            false
        }
    }

    fun isValidPhoneNumber(phone: String): Boolean {
        // Basic phone validation - can be enhanced based on requirements
        val phoneRegex = "^[+]?[0-9]{10,15}$".toRegex()
        return phone.replace("[()\\s-]".toRegex(), "").matches(phoneRegex)
    }

    fun validateOperatingHours(location: DogFriendlyLocation): ValidationResult {
        // Implement operating hours validation if needed
        return ValidationResult(true)
    }

    fun validateAccessibility(location: DogFriendlyLocation): ValidationResult {
        val warnings = mutableListOf<ValidationWarning>()

        // Check for essential accessibility features
        if (!location.hasParking) {
            warnings.add(ValidationWarning.CustomWarning("No parking information available"))
        }

        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }

    fun validateDogSafety(location: DogFriendlyLocation): ValidationResult {
        val warnings = mutableListOf<ValidationWarning>()

        // Check for essential safety features for dogs
        if (location.isDogPark && !location.hasFencing) {
            warnings.add(ValidationWarning.CustomWarning("Dog park not fully fenced"))
        }

        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }

    fun getValidationSummary(result: ValidationResult): String {
        return buildString {
            if (!result.isValid) {
                appendLine("Validation Errors:")
                result.errors.forEach { error ->
                    appendLine("- ${formatError(error)}")
                }
            }
            if (result.warnings.isNotEmpty()) {
                appendLine("Warnings:")
                result.warnings.forEach { warning ->
                    appendLine("- ${formatWarning(warning)}")
                }
            }
        }
    }

    private fun formatError(error: ValidationError): String {
        return when (error) {
            is ValidationError.InvalidCoordinates -> "Invalid coordinates"
            is ValidationError.MissingRequiredField -> "Missing required field"
            is ValidationError.InvalidWebsiteUrl -> "Invalid website URL"
            is ValidationError.InvalidPhoneNumber -> "Invalid phone number"
            is ValidationError.InvalidPlaceId -> "Invalid place ID"
            is ValidationError.CustomError -> error.message
        }
    }

    private fun formatWarning(warning: ValidationWarning): String {
        return when (warning) {
            is ValidationWarning.LowRating -> "Low rating"
            is ValidationWarning.FewReviews -> "Few reviews"
            is ValidationWarning.MissingPhotos -> "Missing photos"
            is ValidationWarning.UnverifiedLocation -> "Unverified location"
            is ValidationWarning.IncompleteAmenities -> "Incomplete amenities information"
            is ValidationWarning.CustomWarning -> warning.message
        }
    }
}
package io.pawsomepals.app.data.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.firebase.firestore.PropertyName
import io.pawsomepals.app.data.converters.Converters
import io.pawsomepals.app.data.converters.DogFriendlyLocationTypeConverter

@Entity(
    tableName = "dogs",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["breed"]),
        Index(value = ["energyLevel"]),
        Index(value = ["size"])
    ]
)
@TypeConverters(AchievementTypeConverter::class)
data class Dog(
    @PrimaryKey
    var id: String = "",
    var ownerId: String = "",
    var name: String = "",
    var breed: String = "",
    var age: Int = 0,
    var gender: String = "",
    var size: String = "",
    var energyLevel: String = "",
    var friendliness: String = "",
    var profilePictureUrl: String? = null,
    val created: Long? = null,
    val lastActive: Long? = null,



    @field:PropertyName("is_spayed_neutered")
    @get:PropertyName("isSpayedNeutered")
    @set:PropertyName("isSpayedNeutered")
    @ColumnInfo(name = "is_spayed_neutered")
    var isSpayedNeutered: Boolean? = null,


    var friendlyWithDogs: String? = null,
    var friendlyWithChildren: String? = null,
    var friendlyWithStrangers: String? = null,
    var specialNeeds: String? = null,
    var favoriteToy: String? = null,
    var preferredActivities: String? = null,
    var walkFrequency: String? = null,
    var favoriteTreat: String? = null,
    var trainingCertifications: String? = null,
    var trainability: String? = null,
    var exerciseNeeds: String? = null,
    var groomingNeeds: String? = null,
    var weight: Double? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,


    @ColumnInfo(name = "profile_complete")
    val profileComplete: Boolean = false,
    val geoHash: String? = null,

    @field:JvmField
    @TypeConverters(Converters::class)
    val photoUrls: List<String?> = List(6) { null },




    @TypeConverters(DogFriendlyLocationTypeConverter::class)
    val frequentedAreas: List<DogFriendlyLocation>? = null,

    @TypeConverters(AchievementTypeConverter::class)
    val achievements: List<Achievement> = emptyList()
) {
    // No-argument constructor required by Firebase
    constructor() : this(
        id = "",
        ownerId = "",
        name = "",
        breed = "",
        age = 0,
        gender = "",
        size = "",
        energyLevel = "",
        friendliness = "",
        isSpayedNeutered = null
    )

    companion object {
        const val MAX_PHOTOS = 6

        // TypeConverter functions
        @JvmStatic
        @TypeConverter
        fun fromStringToBoolean(value: String?): Boolean? {
            return when (value?.toLowerCase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

        @JvmStatic
        @TypeConverter
        fun fromBooleanToString(value: Boolean?): String? {
            return value?.toString()
        }

        // Constants
        object Size {
            const val SMALL = "SMALL"
            const val MEDIUM = "MEDIUM"
            const val LARGE = "LARGE"
        }

        object EnergyLevel {
            const val LOW = "LOW"
            const val MEDIUM = "MEDIUM"
            const val HIGH = "HIGH"
        }

        object Gender {
            const val MALE = "MALE"
            const val FEMALE = "FEMALE"
        }
    }
    @Keep
    @JvmName("setSpayedNeuteredFirestore")
    fun setSpayedNeutered(value: Boolean?) {
        isSpayedNeutered = value
    }



    fun addPhotoUrl(url: String, index: Int): List<String?> {
        if (index !in 0 until MAX_PHOTOS) return photoUrls
        return photoUrls.toMutableList().apply {
            this[index] = url
        }
    }

    fun hasRequiredVaccinations(): Boolean {
        return true // Placeholder
    }

    fun checkProfileComplete(): Boolean {
        return name.isNotBlank() &&
                breed.isNotBlank() &&
                age > 0 &&
                gender.isNotBlank() &&
                size.isNotBlank() &&
                energyLevel.isNotBlank() &&
                !photoUrls.all { it == null }
    }

    fun isInRange(latitude: Double, longitude: Double, radiusKm: Double): Boolean {
        if (this.latitude == null || this.longitude == null) return false

        val earthRadius = 6371 // Earth's radius in kilometers
        val dLat = Math.toRadians(this.latitude!! - latitude)
        val dLon = Math.toRadians(this.longitude!! - longitude)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(this.latitude!!)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = earthRadius * c

        return distance <= radiusKm
    }
}
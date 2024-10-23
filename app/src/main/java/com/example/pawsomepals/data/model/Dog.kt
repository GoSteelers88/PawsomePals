package com.example.pawsomepals.data.model

import androidx.room.*
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
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
    indices = [Index(value = ["ownerId"])]
)
@TypeConverters(AchievementTypeConverter::class)
data class Dog(
    @PrimaryKey var id: String = "",
    var ownerId: String = "",
    var name: String = "",
    var breed: String = "",
    var age: Int = 0,
    var gender: String = "",
    var size: String = "",
    var energyLevel: String = "",
    var friendliness: String = "",
    var profilePictureUrl: String? = null,
    var isSpayedNeutered: String? = null,
    var friendlyWithDogs: String? = null,
    var friendlyWithChildren: String? = null,
    var specialNeeds: String? = null,
    var favoriteToy: String? = null,
    var preferredActivities: String? = null,
    var walkFrequency: String? = null,
    var favoriteTreat: String? = null,
    var trainingCertifications: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    val photoUrls: List<String?> = List(6) { null },
    var trainability: String? = null,
    var friendlyWithStrangers: String? = null,
    var exerciseNeeds: String? = null,
    var groomingNeeds: String? = null,
    var weight: Double? = null,
    @TypeConverters(AchievementTypeConverter::class)
    val achievements: List<Achievement> = emptyList()
) {
    // No-argument constructor required by Firebase
    constructor() : this("", "", "", "", 0, "", "", "", "")
}

data class Achievement(
    val title: String = "",
    val description: String = ""
)
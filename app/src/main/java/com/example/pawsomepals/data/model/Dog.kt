package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    var longitude: Double? = null
) {
    // No-argument constructor required by Firebase
    constructor() : this("", "", "", "", 0, "", "", "", "")
}
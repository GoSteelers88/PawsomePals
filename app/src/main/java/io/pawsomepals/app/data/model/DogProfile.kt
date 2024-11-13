package io.pawsomepals.app.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class DogProfile(
        var id: String = "",
        var ownerId: String = "",
        var name: String = "",
        var breed: String = "",
        var age: Int = 0,
        var gender: String = "",
        var size: String = "",
        var energyLevel: String = "",
        var friendliness: String = "",
        var isSpayedNeutered: Boolean? = null,
        var friendlyWithDogs: String? = null,
        var friendlyWithChildren: String? = null,
        var specialNeeds: String? = null,
        var favoriteToy: String? = null,
        var preferredActivities: String? = null,
        var walkFrequency: String? = null,
        var favoriteTreat: String? = null,
        var trainingCertifications: String? = null,
        var bio: String? = null,
        var profilePictureUrl: String? = null,
        var latitude: Double? = null,
        var longitude: Double? = null
) {
        // No-argument constructor required by Firebase
        constructor() : this("", "", "", "", 0, "", "", "", "")
}
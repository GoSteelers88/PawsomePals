package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.pawsomepals.data.Converters
import com.google.firebase.database.IgnoreExtraProperties
import java.time.LocalDate

@IgnoreExtraProperties
@Entity(tableName = "users")
@TypeConverters(LocalDateConverter::class, Converters::class)
data class User(
    @PrimaryKey var id: String = "",
    var username: String = "",
    var email: String = "",
    var password: String = "",
    var petName: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var bio: String? = null,
    var profilePictureUrl: String? = null,
    var hasAcceptedTerms: Boolean = false,
    var hasCompletedQuestionnaire: Boolean = false,
    @TypeConverters(Converters::class)
    var questionnaireAnswers: Map<String, String>? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    var lastLoginTime: Long = System.currentTimeMillis(),
    val hasSubscription: Boolean = false,
    var subscriptionEndDate: LocalDate? = null,
    var dailyQuestionCount: Int = 0,
    var phoneNumber: String? = null,
    var preferredContact: String? = null,
    var notificationsEnabled: Boolean = true
) {
    // No-argument constructor required by Firebase
    constructor() : this("", "", "", "")
}
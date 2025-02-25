package io.pawsomepals.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.firebase.database.IgnoreExtraProperties
import io.pawsomepals.app.data.converters.Converters
import io.pawsomepals.app.data.converters.LocalDateConverter
import java.time.LocalDate

@IgnoreExtraProperties
@Entity(tableName = "users")
@TypeConverters(LocalDateConverter::class, Converters::class)
data class User(
    @PrimaryKey
    var id: String = "",

    // Basic user info
    var username: String = "",
    var email: String = "",

    var firstName: String? = null,
    var lastName: String? = null,
    var bio: String? = null,

    // Profile related
    var profilePictureUrl: String? = null,

    // Dog relationship
    @TypeConverters(Converters::class)
    var dogIds: List<String> = emptyList(),

    // User state
    var hasAcceptedTerms: Boolean = false,
    var hasCompletedQuestionnaire: Boolean = false,
    var lastLoginTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = System.currentTimeMillis(), // Add this field



    // Location
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Subscription and features
    val hasSubscription: Boolean = false,
    var subscriptionEndDate: LocalDate? = null,
    var dailyQuestionCount: Int = 0,

    // Contact preferences
    var phoneNumber: String? = null,
    var preferredContact: String? = null,
    var notificationsEnabled: Boolean = true,

    // Firestore fields
    @field:JvmField
    var uid: String? = null,
    @field:JvmField
    var displayName: String? = null,
) {
    // No-argument constructor required by Firebase
    constructor() : this(id = "", username = "", email = "")

    // Initialize Firestore-specific fields
    init {
        uid = id
        displayName = username
    }
}
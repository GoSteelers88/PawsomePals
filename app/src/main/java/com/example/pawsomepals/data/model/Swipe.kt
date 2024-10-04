package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
@Entity(tableName = "swipes")
data class Swipe(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    var swiperId: String = "",
    var swipedId: String = "",
    var isLike: Boolean = false,
    var timestamp: Long = System.currentTimeMillis()
) {
    // No-argument constructor required by Firebase
    constructor() : this(0, "", "", false)
}
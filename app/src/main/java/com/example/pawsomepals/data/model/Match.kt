package com.example.pawsomepals.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
@Entity(tableName = "matches")
data class Match(
    @PrimaryKey var id: String = "",
    var user1Id: String = "",
    var user2Id: String = "",
    var timestamp: Long = System.currentTimeMillis()
) {
    // No-argument constructor required by Firebase
    constructor() : this("", "", "")
}
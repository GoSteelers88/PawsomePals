package io.pawsomepals.app.utils

data class Location(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)
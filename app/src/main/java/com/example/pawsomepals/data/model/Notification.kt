package com.example.pawsomepals.data.model

data class Notification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val read: Boolean = false,
    val type: NotificationType = NotificationType.GENERAL
)

enum class NotificationType {
    GENERAL,
    PLAYDATE_REQUEST,
    PLAYDATE_ACCEPTED,
    PLAYDATE_DECLINED,
    NEW_MATCH
}
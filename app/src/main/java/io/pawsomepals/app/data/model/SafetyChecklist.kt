// SafetyChecklist.kt
package io.pawsomepals.app.data.model


data class SafetyChecklist(
    val vaccinationVerified: Boolean = false,
    val sizeCompatible: Boolean = false,
    val energyLevelMatched: Boolean = false,
    val meetingSpotConfirmed: Boolean = false,
    val backupContactShared: Boolean = false
)



data class ChecklistItem(
    val text: String,
    var checked: Boolean
)
package io.pawsomepals.app.data.model

import java.time.LocalDate
import java.time.LocalTime

data class SchedulingState(
    val currentStep: SchedulingStep = SchedulingStep.LOCATION,
    val selectedLocation: DogFriendlyLocation? = null,
    val selectedDate: LocalDate? = null,
    val selectedTime: LocalTime? = null,
    val availableTimeSlots: List<TimeSlot> = emptyList(),
    val error: String? = null
)

enum class SchedulingStep {
    LOCATION,
    SCHEDULE,
    DATE,
    TIME,
    COMPLETE,// Combined DATE and TIME

    REVIEW
}



data class Weather(
    val temperature: Double,
    val condition: String,
    val isGoodForOutdoor: Boolean
)
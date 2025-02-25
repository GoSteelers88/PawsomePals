package io.pawsomepals.app.data.model

import java.time.LocalDate
import java.time.LocalTime


data class SchedulingState(
    val currentStep: SchedulingStep = SchedulingStep.DOG_SELECTION,
    val selectedDog: Dog? = null,
    val selectedLocation: DogFriendlyLocation? = null,
    val selectedDate: LocalDate? = null,
    val selectedTime: LocalTime? = null,
    val availableTimeSlots: List<TimeSlot> = emptyList(),
    val selectedTimeSlot: TimeSlot? = null,
    val weather: Weather? = null,
    val request: PlaydateRequest? = null,
    val error: String? = null
) {
    fun isValid(): Boolean = selectedDog != null &&    // Added dog validation
            selectedDate != null &&
            selectedTime != null &&
            selectedTimeSlot != null
}

enum class SchedulingStep {
    DOG_SELECTION,
    LOCATION,
    DATE,
    TIME,
    REVIEW,
    COMPLETE
}

data class Weather(
    val temperature: Double,
    val condition: String,
    val isGoodForOutdoor: Boolean,
    val precipitation: Double? = null,
    val windSpeed: Double? = null
)
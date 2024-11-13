// QuestionnaireData.kt
package io.pawsomepals.app.data.model

enum class QuestionType {
    TEXT,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    NUMBER,
    BOOLEAN
}

data class QuestionnaireQuestion(
    val id: String,            // Maps to Dog model field
    val text: String,          // Question text shown to user
    val type: QuestionType,
    val required: Boolean = true,
    val options: List<String>? = null,
    val validation: QuestionValidation? = null
)

data class QuestionValidation(
    val minValue: Number? = null,
    val maxValue: Number? = null,
    val regex: String? = null,
    val errorMessage: String? = null
)

data class QuestionnaireCategory(
    val title: String,
    val questions: List<QuestionnaireQuestion>
)

object QuestionnaireData {
    val categories = listOf(
        QuestionnaireCategory(
            title = "Basic Information",
            questions = listOf(
                QuestionnaireQuestion(
                    id = "name",              // Maps to Dog.name
                    text = "What's your dog's name?",
                    type = QuestionType.TEXT,
                    validation = QuestionValidation(
                        regex = "^[a-zA-Z0-9\\s]{2,30}$",
                        errorMessage = "Name must be 2-30 characters long"
                    )
                ),
                QuestionnaireQuestion(
                    id = "breed",             // Maps to Dog.breed
                    text = "What breed is your dog?",
                    type = QuestionType.TEXT
                ),
                QuestionnaireQuestion(
                    id = "age",               // Maps to Dog.age
                    text = "How old is your dog?",
                    type = QuestionType.NUMBER,
                    validation = QuestionValidation(
                        minValue = 0,
                        maxValue = 25,
                        errorMessage = "Age must be between 0-25 years"
                    )
                ),
                QuestionnaireQuestion(
                    id = "weight",            // Maps to Dog.weight
                    text = "What is your dog's weight (in kg)?",
                    type = QuestionType.NUMBER,
                    validation = QuestionValidation(
                        minValue = 0.5,
                        maxValue = 100.0,
                        errorMessage = "Weight must be between 0.5-100 kg"
                    )
                )
            )
        ),
        QuestionnaireCategory(
            title = "Temperament & Behavior",
            questions = listOf(
                QuestionnaireQuestion(
                    id = "energyLevel",       // Maps to Dog.energyLevel
                    text = "What is your dog's energy level?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("Low", "Medium", "High")
                ),
                QuestionnaireQuestion(
                    id = "friendliness",      // Maps to Dog.friendliness
                    text = "How friendly is your dog generally?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("Shy", "Moderate", "Very Friendly")
                ),
                QuestionnaireQuestion(
                    id = "friendlyWithDogs",  // Maps to Dog.friendlyWithDogs
                    text = "How does your dog interact with other dogs?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("Cautious", "Neutral", "Friendly", "Very Friendly")
                ),
                QuestionnaireQuestion(
                    id = "trainability",      // Maps to Dog.trainability
                    text = "How would you rate your dog's trainability?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("Needs Work", "Good", "Excellent")
                )
            )
        ),
        QuestionnaireCategory(
            title = "Care & Preferences",
            questions = listOf(
                QuestionnaireQuestion(
                    id = "exerciseNeeds",     // Maps to Dog.exerciseNeeds
                    text = "What are your dog's exercise needs?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("Light", "Moderate", "High")
                ),
                QuestionnaireQuestion(
                    id = "walkFrequency",     // Maps to Dog.walkFrequency
                    text = "How often does your dog need walks?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("1-2 times/day", "2-3 times/day", "3+ times/day")
                ),
                QuestionnaireQuestion(
                    id = "preferredActivities", // Maps to Dog.preferredActivities
                    text = "What activities does your dog enjoy?",
                    type = QuestionType.MULTIPLE_CHOICE,
                    options = listOf("Walking", "Running", "Fetch", "Tug of War", "Swimming", "Dog Park")
                ),
                QuestionnaireQuestion(
                    id = "specialNeeds",      // Maps to Dog.specialNeeds
                    text = "Does your dog have any special needs or requirements?",
                    type = QuestionType.TEXT,
                    required = false
                )
            )
        )
    )
}
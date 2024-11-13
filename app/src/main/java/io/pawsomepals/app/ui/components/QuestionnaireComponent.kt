package io.pawsomepals.app.ui.components

import androidx.compose.runtime.Stable

@Stable
data class QuestionnaireCategory(
    val name: String,
    val questions: List<Question>
)

@Stable
data class Question(
    val id: String,
    val text: String,
    val type: QuestionType,
    val options: List<String>? = null
)

@Stable
data class QuestionProgress(
    val categoryIndex: Int,
    val questionIndex: Int,
    val totalQuestions: Int,
    val categoryName: String
)

enum class QuestionType {
    TEXT,
    SINGLE_CHOICE,
    MULTI_CHOICE
}

object QuestionnaireData {
    fun getQuestionnaireCategories(): List<QuestionnaireCategory> = listOf(
        QuestionnaireCategory(
            name = "Basic Information",
            questions = listOf(
                Question(
                    id = "dog_name",
                    text = "What's your dog's name?",
                    type = QuestionType.TEXT
                ),
                Question(
                    id = "dog_breed",
                    text = "What breed is your dog?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Labrador Retriever",
                        "German Shepherd",
                        "Golden Retriever",
                        "French Bulldog",
                        "Bulldog",
                        "Poodle",
                        "Beagle",
                        "Rottweiler",
                        "Pointer",
                        "Dachshund",
                        "Yorkshire Terrier",
                        "Boxer",
                        "Siberian Husky",
                        "Great Dane",
                        "Doberman Pinscher",
                        "Other"
                    )
                ),
                Question(
                    id = "dog_age",
                    text = "How old is your dog?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Less than 1 year",
                        "1-3 years",
                        "4-7 years",
                        "8-10 years",
                        "Over 10 years"
                    )
                ),
                Question(
                    id = "dog_gender",
                    text = "What's your dog's gender?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("Male", "Female")
                ),
                Question(
                    id = "dog_neutered",
                    text = "Is your dog spayed/neutered?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf("Yes", "No")
                ),
                Question(
                    id = "dog_size",
                    text = "What's your dog's size?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Extra Small (0-10 lbs)",
                        "Small (11-25 lbs)",
                        "Medium (26-50 lbs)",
                        "Large (51-100 lbs)",
                        "Extra Large (100+ lbs)"
                    )
                ),
                Question(
                    id = "dog_weight",
                    text = "What's your dog's weight in kg?",
                    type = QuestionType.TEXT
                )
            )
        ),

        QuestionnaireCategory(
            name = "Personality & Behavior",
            questions = listOf(
                Question(
                    id = "dog_energy",
                    text = "How would you describe your dog's energy level?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Very Low",
                        "Low",
                        "Moderate",
                        "High",
                        "Very High"
                    )
                ),
                Question(
                    id = "dog_friendliness",
                    text = "How friendly is your dog in general?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Very Friendly",
                        "Friendly",
                        "Neutral",
                        "Somewhat Unfriendly",
                        "Not Friendly"
                    )
                ),
                Question(
                    id = "dog_trainability",
                    text = "How would you rate your dog's trainability?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Very Easy",
                        "Easy",
                        "Moderate",
                        "Difficult",
                        "Very Difficult"
                    )
                )
            )
        ),

        QuestionnaireCategory(
            name = "Social Compatibility",
            questions = listOf(
                Question(
                    id = "dog_friendly_dogs",
                    text = "How friendly is your dog with other dogs?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Very Friendly",
                        "Friendly",
                        "Neutral",
                        "Somewhat Unfriendly",
                        "Not Friendly"
                    )
                ),
                Question(
                    id = "dog_friendly_children",
                    text = "How friendly is your dog with children?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Very Friendly",
                        "Friendly",
                        "Neutral",
                        "Somewhat Unfriendly",
                        "Not Friendly",
                        "Unknown"
                    )
                ),
                Question(
                    id = "dog_friendly_strangers",
                    text = "How friendly is your dog with strangers?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Very Friendly",
                        "Friendly",
                        "Neutral",
                        "Somewhat Unfriendly",
                        "Not Friendly"
                    )
                )
            )
        ),

        QuestionnaireCategory(
            name = "Care Information",
            questions = listOf(
                Question(
                    id = "dog_exercise_needs",
                    text = "What are your dog's exercise needs?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Very Low",
                        "Low",
                        "Moderate",
                        "High",
                        "Very High"
                    )
                ),
                Question(
                    id = "dog_grooming_needs",
                    text = "What are your dog's grooming needs?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Minimal",
                        "Low",
                        "Moderate",
                        "High",
                        "Very High"
                    )
                ),
                Question(
                    id = "dog_special_needs",
                    text = "Does your dog have any special needs or medical conditions?",
                    type = QuestionType.MULTI_CHOICE,
                    options = listOf(
                        "None",
                        "Allergies",
                        "Arthritis",
                        "Blindness",
                        "Deafness",
                        "Diabetes",
                        "Heart Condition",
                        "Hip Dysplasia",
                        "Skin Condition",
                        "Other"
                    )
                ),
                Question(
                    id = "dog_walk_frequency",
                    text = "How often do you take your dog for walks?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Multiple times a day",
                        "Once a day",
                        "A few times a week",
                        "Once a week",
                        "Rarely"
                    )
                )
            )
        ),

        QuestionnaireCategory(
            name = "Preferences & Lifestyle",
            questions = listOf(
                Question(
                    id = "dog_favorite_toy",
                    text = "What's your dog's favorite toy?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Ball",
                        "Frisbee",
                        "Plush Toy",
                        "Rope Toy",
                        "Chew Toy",
                        "Puzzle Toy",
                        "None",
                        "Other"
                    )
                ),
                Question(
                    id = "dog_favorite_treat",
                    text = "What's your dog's favorite treat?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Commercial dog treats",
                        "Cheese",
                        "Meat",
                        "Fruits/Vegetables",
                        "Homemade treats",
                        "Other"
                    )
                ),
                Question(
                    id = "dog_preferred_activities",
                    text = "What are your dog's preferred activities?",
                    type = QuestionType.MULTI_CHOICE,
                    options = listOf(
                        "Walking",
                        "Running",
                        "Fetch",
                        "Swimming",
                        "Agility",
                        "Dog Park",
                        "Cuddles",
                        "Other"
                    )
                ),
                Question(
                    id = "dog_indoor_outdoor",
                    text = "Does your dog prefer indoor or outdoor activities?",
                    type = QuestionType.SINGLE_CHOICE,
                    options = listOf(
                        "Mostly Indoor",
                        "Balanced Indoor/Outdoor",
                        "Mostly Outdoor"
                    )
                )
            )
        ),

        QuestionnaireCategory(
            name = "Training & Skills",
            questions = listOf(
                Question(
                    id = "dog_training_certifications",
                    text = "Does your dog have any training certifications?",
                    type = QuestionType.MULTI_CHOICE,
                    options = listOf(
                        "None",
                        "Basic Obedience",
                        "Advanced Obedience",
                        "Agility",
                        "Therapy Dog",
                        "Search and Rescue",
                        "Canine Good Citizen",
                        "Other"
                    )
                )
            )
        )
    )

    // Utility functions for questionnaire data
    fun getQuestionById(id: String): Question? {
        return getQuestionnaireCategories()
            .flatMap { it.questions }
            .find { it.id == id }
    }

    fun getCategoryForQuestion(questionId: String): QuestionnaireCategory? {
        return getQuestionnaireCategories()
            .find { category ->
                category.questions.any { it.id == questionId }
            }
    }

    fun validateAnswer(question: Question, answer: String?): Boolean {
        return when (question.type) {
            QuestionType.TEXT -> !answer.isNullOrBlank()
            QuestionType.SINGLE_CHOICE -> !answer.isNullOrBlank() && question.options?.contains(answer) == true
            QuestionType.MULTI_CHOICE -> {
                val selectedOptions = answer?.split(",") ?: emptyList()
                selectedOptions.isNotEmpty() &&
                        selectedOptions.first().isNotEmpty() &&
                        selectedOptions.all { option -> question.options?.contains(option) == true }
            }
        }
    }

    fun getTotalQuestions(): Int = getQuestionnaireCategories()
        .sumOf { it.questions.size }
}
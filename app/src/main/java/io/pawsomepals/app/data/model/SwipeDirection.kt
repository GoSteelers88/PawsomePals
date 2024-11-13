
package io.pawsomepals.app.data.model

/**
 * Enum class representing different swipe directions in the io.pawsomepals.app.
 * Each direction has associated metadata like animations, thresholds, and default actions.
 */
enum class SwipeDirection(
    val angle: Float,
    val threshold: Float = 0.3f,
    val animation: String = "swipe",
    val actionName: String
) {
    LEFT(180f, actionName = "Pass") {
        override fun getSwipeAction() = SwipeAction.PASS
        override fun getAnimationDuration() = 300L
    },

    RIGHT(0f, actionName = "Like") {
        override fun getSwipeAction() = SwipeAction.LIKE
        override fun getAnimationDuration() = 300L
    },

    UP(270f, threshold = 0.5f, actionName = "Super Like") {
        override fun getSwipeAction() = SwipeAction.SUPER_LIKE
        override fun getAnimationDuration() = 400L // Slightly longer for super like
    },

    DOWN(90f, threshold = 0.4f, actionName = "Schedule") {
        override fun getSwipeAction() = SwipeAction.SCHEDULE
        override fun getAnimationDuration() = 300L
    },

    NONE(0f, threshold = 0f, actionName = "None") {
        override fun getSwipeAction() = SwipeAction.NONE
        override fun getAnimationDuration() = 0L
    };

    /**
     * Abstract methods to be implemented by each direction
     */
    abstract fun getSwipeAction(): SwipeAction
    abstract fun getAnimationDuration(): Long

    /**
     * Helper methods for swipe processing
     */
    fun isValidSwipe(distance: Float): Boolean {
        return distance >= threshold
    }

    fun getSwipeProgress(distance: Float): Float {
        return (distance / threshold).coerceIn(0f, 1f)
    }

    companion object {
        fun fromAngle(angle: Float): SwipeDirection {
            return when (angle) {
                in 135f..225f -> LEFT
                in 315f..360f, in 0f..45f -> RIGHT
                in 225f..315f -> UP
                in 45f..135f -> DOWN
                else -> NONE
            }
        }

        fun fromAction(action: SwipeAction): SwipeDirection {
            return when (action) {
                SwipeAction.PASS -> LEFT
                SwipeAction.LIKE -> RIGHT
                SwipeAction.SUPER_LIKE -> UP
                SwipeAction.SCHEDULE -> DOWN
                SwipeAction.NONE -> NONE
            }
        }
    }
}

/**
 * Enum representing possible swipe actions
 */
enum class SwipeAction {
    PASS,
    LIKE,
    SUPER_LIKE,
    SCHEDULE,
    NONE;

    fun getIcon(): String = when(this) {
        PASS -> "❌"
        LIKE -> "❤️"
        SUPER_LIKE -> "⭐"
        SCHEDULE -> "📅"
        NONE -> ""
    }

    fun getColor(): String = when(this) {
        PASS -> "#FF5252"     // Red
        LIKE -> "#4CAF50"     // Green
        SUPER_LIKE -> "#2196F3" // Blue
        SCHEDULE -> "#9C27B0"   // Purple
        NONE -> "#000000"     // Black
    }
}

/**
 * Data class to hold swipe event details
 */
data class SwipeEvent(
    val direction: SwipeDirection,
    val distance: Float,
    val velocity: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isValid: Boolean
        get() = direction.isValidSwipe(distance)

    val progress: Float
        get() = direction.getSwipeProgress(distance)

    val action: SwipeAction
        get() = direction.getSwipeAction()
}

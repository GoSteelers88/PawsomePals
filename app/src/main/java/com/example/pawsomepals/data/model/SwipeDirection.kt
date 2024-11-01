package com.example.pawsomepals.data.model

enum class SwipeDirection {
    LEFT,       // Not interested
    RIGHT,      // Interested
    UP,         // Super like
    DOWN,       // Report/Block
    NONE;       // Default state

    companion object {
        fun fromString(value: String?): SwipeDirection {
            return try {
                if (value == null) NONE
                else valueOf(value)
            } catch (e: IllegalArgumentException) {
                NONE
            }
        }

        fun isPositiveSwipe(direction: SwipeDirection): Boolean {
            return direction == RIGHT || direction == UP
        }

        fun isNegativeSwipe(direction: SwipeDirection): Boolean {
            return direction == LEFT || direction == DOWN
        }
    }
}
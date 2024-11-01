package com.example.pawsomepals.data.model

enum class MatchStatus {
    PENDING,    // Initial state when a match is created
    ACTIVE,     // Both users have accepted
    DECLINED,   // One user has declined
    EXPIRED,    // No response within time limit
    BLOCKED,    // One user has blocked the match
    ARCHIVED;   // Match has been archived by user

    companion object {
        fun fromString(value: String?): MatchStatus {
            return try {
                if (value == null) PENDING
                else valueOf(value)
            } catch (e: IllegalArgumentException) {
                PENDING
            }
        }

        fun isTerminalState(status: MatchStatus): Boolean {
            return status in listOf(DECLINED, EXPIRED, BLOCKED)
        }

        fun canTransitionTo(from: MatchStatus, to: MatchStatus): Boolean {
            return when (from) {
                PENDING -> to in listOf(ACTIVE, DECLINED, EXPIRED)
                ACTIVE -> to in listOf(BLOCKED, ARCHIVED)
                ARCHIVED -> to == ACTIVE
                else -> false
            }
        }
    }
}
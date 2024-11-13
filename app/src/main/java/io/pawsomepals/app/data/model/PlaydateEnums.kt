// PlaydateEnums.kt
package io.pawsomepals.app.data.model

enum class PlaydateStatus {
    NONE,
    PENDING,
    ACCEPTED,
    MATCHED,
    DECLINED,
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    RESCHEDULING,
    CONFIRMED;    // Added this status

    fun getIcon(): String = when(this) {
        NONE -> "❓"
        PENDING -> "⏳"
        ACCEPTED -> "✅"
        DECLINED -> "❌"
        SCHEDULED -> "📅"
        COMPLETED -> "🎉"
        CANCELLED -> "🚫"
        RESCHEDULING -> "🔄"
        MATCHED -> "🤝"
        CONFIRMED -> TODO()
    }

    fun getDescription(): String = when(this) {
        NONE -> "No status"
        PENDING -> "Waiting for response"
        ACCEPTED -> "Playdate accepted"
        MATCHED -> "Dogs matched"  // Added this
        DECLINED -> "Playdate declined"
        SCHEDULED -> "Playdate scheduled"
        COMPLETED -> "Playdate completed"
        CANCELLED -> "Playdate cancelled"
        RESCHEDULING -> "Playdate being rescheduled"
        CONFIRMED -> TODO()
    }
}

enum class PlaydateMood {
    EXCELLENT {
        override fun getIcon() = "🌟"
        override fun getDescription() = "Had an amazing time!"
    },
    GOOD {
        override fun getIcon() = "😊"
        override fun getDescription() = "Had a good time"
    },
    NEUTRAL {
        override fun getIcon() = "😐"
        override fun getDescription() = "It was okay"
    },
    NEEDS_IMPROVEMENT {
        override fun getIcon() = "😕"
        override fun getDescription() = "Could have been better"
    };

    abstract fun getIcon(): String
    abstract fun getDescription(): String
}
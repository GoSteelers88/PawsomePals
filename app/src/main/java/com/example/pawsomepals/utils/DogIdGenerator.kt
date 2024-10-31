package com.example.pawsomepals.utils

object DogIdGenerator {
    /**
     * Generates a unique dog ID with the format:
     * DOG_[userIdPrefix]_[timestamp]_[random]_[checksum]
     * Example: DOG_abc123_1698765432123_7391_5F
     */
    fun generate(userId: String): String {
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random()
        val userIdPrefix = userId.take(6)
        val baseId = "DOG_${userIdPrefix}_${timestamp}_$random"
        val checksum = generateChecksum(baseId)
        return "${baseId}_$checksum"
    }

    /**
     * Validates a dog ID format and checksum
     */
    fun isValid(dogId: String): Boolean {
        val parts = dogId.split("_")
        if (parts.size != 5) return false // DOG_prefix_timestamp_random_checksum
        if (parts[0] != "DOG") return false

        val baseId = parts.subList(0, 4).joinToString("_")
        val checksum = parts[4]
        return checksum == generateChecksum(baseId)
    }

    /**
     * Extracts the owner's user ID prefix from a dog ID
     */
    fun extractUserIdPrefix(dogId: String): String? {
        return try {
            dogId.split("_")[1]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts the timestamp from a dog ID
     */
    fun extractTimestamp(dogId: String): Long? {
        return try {
            dogId.split("_")[2].toLong()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates a simple checksum for ID validation
     */
    private fun generateChecksum(baseId: String): String {
        return baseId.fold(0) { acc, char ->
            (acc + char.code) % 256
        }.toString(16).uppercase().padStart(2, '0')
    }
}
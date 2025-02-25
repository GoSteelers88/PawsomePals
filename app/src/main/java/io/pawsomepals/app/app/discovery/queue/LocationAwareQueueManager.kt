package io.pawsomepals.app.discovery.queue

import android.util.Log
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.service.location.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationAwareQueueManager @Inject constructor(
    private val locationService: LocationService
) {
    companion object {
        private const val TAG = "LocationAwareQueue"

        // Distance bands in kilometers
        private const val VERY_CLOSE_DISTANCE = 5.0
        private const val CLOSE_DISTANCE = 10.0
        private const val MEDIUM_DISTANCE = 20.0
        private const val FAR_DISTANCE = 50.0
    }

    // Distance-based queue buckets
    private val veryCloseQueue = mutableListOf<ProfileQueueItem>()
    private val closeQueue = mutableListOf<ProfileQueueItem>()
    private val mediumQueue = mutableListOf<ProfileQueueItem>()
    private val farQueue = mutableListOf<ProfileQueueItem>()

    // State tracking
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    data class ProfileQueueItem(
        val dog: Dog,
        val distanceKm: Double,
        val addedTime: Long = System.currentTimeMillis()
    )

    /**
     * Adds a single profile to the appropriate distance-based queue
     */
    fun addToQueue(dog: Dog, distanceKm: Double) {
        val queueItem = ProfileQueueItem(dog, distanceKm)

        synchronized(this) {
            when {
                distanceKm <= VERY_CLOSE_DISTANCE -> veryCloseQueue.add(queueItem)
                distanceKm <= CLOSE_DISTANCE -> closeQueue.add(queueItem)
                distanceKm <= MEDIUM_DISTANCE -> mediumQueue.add(queueItem)
                distanceKm <= FAR_DISTANCE -> farQueue.add(queueItem)
            }
            updateQueueSize()
        }
        Log.d(TAG, "Added dog ${dog.id} at distance $distanceKm km to queue")
    }

    /**
     * Adds multiple profiles to the queue with distance calculations
     */
    fun addBatchToQueue(dogs: List<Dog>, currentLat: Double, currentLng: Double) {
        dogs.forEach { dog ->
            val distance = if (dog.latitude != null && dog.longitude != null) {
                locationService.calculateDistance(
                    currentLat,
                    currentLng,
                    dog.latitude!!,
                    dog.longitude!!
                ).toDouble()
            } else {
                FAR_DISTANCE
            }
            addToQueue(dog, distance)
        }
        Log.d(TAG, "Added batch of ${dogs.size} dogs to queue")
    }

    /**
     * Gets the next batch of profiles, prioritizing by distance
     */
    fun getNextBatch(batchSize: Int): List<Dog> {
        synchronized(this) {
            val batch = mutableListOf<Dog>()
            var remainingSize = batchSize

            // Priority distribution
            val veryCloseCount = (remainingSize * 0.4).toInt()
            remainingSize -= addFromQueue(veryCloseQueue, veryCloseCount, batch)

            val closeCount = (remainingSize * 0.3).toInt()
            remainingSize -= addFromQueue(closeQueue, closeCount, batch)

            val mediumCount = (remainingSize * 0.2).toInt()
            remainingSize -= addFromQueue(mediumQueue, mediumCount, batch)

            // Fill remaining with far profiles
            addFromQueue(farQueue, remainingSize, batch)

            updateQueueSize()
            Log.d(TAG, "Retrieved batch of ${batch.size} profiles")
            return batch
        }
    }

    /**
     * Removes a profile from all queues
     */
    fun removeFromQueue(dogId: String) {
        synchronized(this) {
            veryCloseQueue.removeIf { it.dog.id == dogId }
            closeQueue.removeIf { it.dog.id == dogId }
            mediumQueue.removeIf { it.dog.id == dogId }
            farQueue.removeIf { it.dog.id == dogId }
            updateQueueSize()
        }
        Log.d(TAG, "Removed dog $dogId from queue")
    }

    /**
     * Clears all queues
     */
    fun clearQueue() {
        synchronized(this) {
            veryCloseQueue.clear()
            closeQueue.clear()
            mediumQueue.clear()
            farQueue.clear()
            updateQueueSize()
        }
        Log.d(TAG, "Cleared all queues")
    }

    /**
     * Gets the current size of all queues combined
     */
    fun getTotalQueueSize(): Int {
        return synchronized(this) {
            veryCloseQueue.size + closeQueue.size + mediumQueue.size + farQueue.size
        }
    }

    /**
     * Updates queue statistics
     */
    private fun updateQueueSize() {
        _queueSize.value = getTotalQueueSize()
        Log.d(TAG, """
            Queue sizes:
            - Very Close (0-5km): ${veryCloseQueue.size}
            - Close (5-10km): ${closeQueue.size}
            - Medium (10-20km): ${mediumQueue.size}
            - Far (20-50km): ${farQueue.size}
            - Total: ${_queueSize.value}
        """.trimIndent())
    }

    /**
     * Adds profiles from a specific queue to the batch
     */
    private fun addFromQueue(
        queue: MutableList<ProfileQueueItem>,
        count: Int,
        batch: MutableList<Dog>
    ): Int {
        if (count <= 0) return 0

        val itemsToAdd = queue.take(count)
        batch.addAll(itemsToAdd.map { it.dog })
        queue.removeAll(itemsToAdd)
        return itemsToAdd.size
    }

    /**
     * Removes expired profiles from queues
     */
    fun cleanupExpiredProfiles(maxAgeMs: Long = 3600000) { // 1 hour default
        val now = System.currentTimeMillis()
        synchronized(this) {
            listOf(veryCloseQueue, closeQueue, mediumQueue, farQueue).forEach { queue ->
                queue.removeIf { (now - it.addedTime) > maxAgeMs }
            }
            updateQueueSize()
        }
    }

    /**
     * Gets queue statistics for monitoring
     */
    fun getQueueStats(): QueueStats {
        return synchronized(this) {
            QueueStats(
                veryCloseCount = veryCloseQueue.size,
                closeCount = closeQueue.size,
                mediumCount = mediumQueue.size,
                farCount = farQueue.size,
                totalCount = getTotalQueueSize()
            )
        }
    }

    data class QueueStats(
        val veryCloseCount: Int,
        val closeCount: Int,
        val mediumCount: Int,
        val farCount: Int,
        val totalCount: Int
    )
}
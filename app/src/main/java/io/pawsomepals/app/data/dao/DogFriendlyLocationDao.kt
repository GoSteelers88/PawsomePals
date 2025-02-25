package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.pawsomepals.app.data.model.DogFriendlyLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface DogFriendlyLocationDao {
    @Query("SELECT * FROM dog_friendly_locations")
    fun getAllLocations(): Flow<List<DogFriendlyLocation>>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLng AND :maxLng
    """)
    suspend fun getLocationsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): List<DogFriendlyLocation>

    @Query("""
        SELECT *, 
        ((:currentLat - latitude) * (:currentLat - latitude) + 
        (:currentLng - longitude) * (:currentLng - longitude)) as distance 
        FROM dog_friendly_locations 
        WHERE ((:currentLat - latitude) * (:currentLat - latitude) + 
        (:currentLng - longitude) * (:currentLng - longitude)) <= (:radiusInDegrees * :radiusInDegrees)
        ORDER BY distance ASC
    """)
    suspend fun getNearbyLocations(
        currentLat: Double,
        currentLng: Double,
        radiusInDegrees: Double
    ): List<DogFriendlyLocation>

    @Query("SELECT * FROM dog_friendly_locations WHERE placeId = :placeId")
    suspend fun getLocationById(placeId: String): DogFriendlyLocation?

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE isDogPark = 1 
        ORDER BY 
        ((:lat - latitude) * (:lat - latitude) + 
        (:lng - longitude) * (:lng - longitude)) ASC 
        LIMIT :limit
    """)
    suspend fun getNearbyDogParks(
        lat: Double,
        lng: Double,
        limit: Int = 10
    ): List<DogFriendlyLocation>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE isVerified = 1 
        AND rating >= :minRating
        ORDER BY rating DESC
    """)
    fun getTopRatedLocations(minRating: Double = 4.0): Flow<List<DogFriendlyLocation>>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE placeTypes LIKE '%' || :venueType || '%'
    """)
    suspend fun getLocationsByType(venueType: String): List<DogFriendlyLocation>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE hasOutdoorSeating = 1 
        AND (servesFood = 1 OR servesDrinks = 1)
        ORDER BY rating DESC
    """)
    suspend fun getDogFriendlyDiningLocations(): List<DogFriendlyLocation>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE isOffLeashAllowed = 1 
        AND hasFencing = 1
    """)
    suspend fun getOffLeashLocations(): List<DogFriendlyLocation>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE hasWaterFountain = 1 
        AND hasWasteStations = 1
    """)
    suspend fun getWellEquippedLocations(): List<DogFriendlyLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: DogFriendlyLocation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<DogFriendlyLocation>)

    @Update
    suspend fun update(location: DogFriendlyLocation)

    @Delete
    suspend fun delete(location: DogFriendlyLocation)

    @Query("DELETE FROM dog_friendly_locations")
    suspend fun clearAll()

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE lastUpdated < :timestamp
    """)
    suspend fun getStaleLocations(timestamp: Long): List<DogFriendlyLocation>

    @Query("""
        UPDATE dog_friendly_locations 
        SET rating = :rating, 
            reviewCount = reviewCount + 1,
            lastUpdated = :timestamp 
        WHERE placeId = :placeId
    """)
    suspend fun updateRating(
        placeId: String,
        rating: Double,
        timestamp: Long = System.currentTimeMillis()
    )

    @Transaction
    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE isVerified = 1 
        AND rating >= 4.0 
        ORDER BY 
        CASE 
            WHEN :sortBy = 'RATING' THEN rating 
            WHEN :sortBy = 'DISTANCE' THEN 
                ((:lat - latitude) * (:lat - latitude) + 
                (:lng - longitude) * (:lng - longitude))
            ELSE reviewCount 
        END DESC 
        LIMIT :limit
    """)
    suspend fun getRecommendedLocations(
        lat: Double,
        lng: Double,
        sortBy: String = "RATING",
        limit: Int = 10
    ): List<DogFriendlyLocation>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE name LIKE '%' || :query || '%' 
        OR address LIKE '%' || :query || '%'
    """)
    suspend fun searchLocations(query: String): List<DogFriendlyLocation>

    @Query("""
        SELECT * FROM dog_friendly_locations 
        WHERE isIndoor = :isIndoor 
        AND hasParking = 1
    """)
    suspend fun getLocationsByIndoorStatus(isIndoor: Boolean): List<DogFriendlyLocation>

    @Query("SELECT * FROM dog_friendly_locations WHERE isFavorite = 1")
    fun getFavoriteLocations(): List<DogFriendlyLocation>

    @Query("""
        UPDATE dog_friendly_locations 
        SET dogFriendlyRating = :rating, 
            lastUpdated = :timestamp 
        WHERE placeId = :placeId
    """)
    suspend fun updateDogFriendlyRating(
        placeId: String,
        rating: Double,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        SELECT COUNT(*) FROM dog_friendly_locations 
        WHERE lastUpdated > :since
    """)
    suspend fun getRecentLocationsCount(since: Long): Int
    @Query("UPDATE dog_friendly_locations SET isFavorite = :isFavorite WHERE placeId = :placeId")
    suspend fun updateFavoriteStatus(placeId: String, isFavorite: Boolean)

    @Query("SELECT isFavorite FROM dog_friendly_locations WHERE placeId = :placeId")
    suspend fun isFavorite(placeId: String): Boolean?

    // Add a query to get nearby favorite locations
    @Query("""
        SELECT *, 
        ((:currentLat - latitude) * (:currentLat - latitude) + 
        (:currentLng - longitude) * (:currentLng - longitude)) as distance 
        FROM dog_friendly_locations 
        WHERE isFavorite = 1
        AND ((:currentLat - latitude) * (:currentLat - latitude) + 
        (:currentLng - longitude) * (:currentLng - longitude)) <= (:radiusInDegrees * :radiusInDegrees)
        ORDER BY distance ASC
    """)
    suspend fun getNearbyFavoriteLocations(
        currentLat: Double,
        currentLng: Double,
        radiusInDegrees: Double
    ): List<DogFriendlyLocation>

    // Add transaction to toggle favorite status
    @Transaction
    suspend fun toggleFavorite(placeId: String): Boolean {
        val currentStatus = isFavorite(placeId) ?: false
        val newStatus = !currentStatus
        updateFavoriteStatus(placeId, newStatus)
        return newStatus
    }
}
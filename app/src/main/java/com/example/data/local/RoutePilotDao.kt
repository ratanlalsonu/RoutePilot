package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePilotDao {

    // Recent Destinations
    @Query("SELECT * FROM recent_destinations ORDER BY lastVisitedTimestamp DESC")
    fun observeAllDestinations(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM recent_destinations WHERE isDemoSample = 0 ORDER BY lastVisitedTimestamp DESC")
    fun observeLiveDestinations(): Flow<List<DestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDestination(destination: DestinationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDestinationsIgnore(destinations: List<DestinationEntity>)

    @Query("DELETE FROM recent_destinations WHERE id = :destinationId")
    suspend fun deleteDestinationById(destinationId: String)

    @Query("DELETE FROM recent_destinations WHERE id LIKE :userPrefix")
    suspend fun deleteDestinationsByPrefix(userPrefix: String)

    @Query("DELETE FROM recent_destinations")
    suspend fun clearAllDestinations()

    // Journeys
    @Query("SELECT * FROM journeys ORDER BY completedAt DESC")
    fun observeAllJourneys(): Flow<List<JourneyEntity>>

    @Query("SELECT * FROM journeys WHERE isDemoRecord = 0 ORDER BY completedAt DESC")
    fun observeLiveJourneys(): Flow<List<JourneyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: JourneyEntity)

    @Query("DELETE FROM journeys")
    suspend fun clearAllJourneys()

    // Cached Hazards (with freshness tracking)
    @Query("SELECT * FROM cached_hazards WHERE active = 1 ORDER BY updatedAt DESC")
    fun observeActiveCachedHazards(): Flow<List<HazardCacheEntity>>

    @Query("SELECT * FROM cached_hazards WHERE id = :id LIMIT 1")
    suspend fun getCachedHazardById(id: String): HazardCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedHazards(hazards: List<HazardCacheEntity>)

    @Query("DELETE FROM cached_hazards WHERE cachedAtTimestamp < :minFreshnessTimestamp")
    suspend fun deleteStaleCachedHazards(minFreshnessTimestamp: Long)
}

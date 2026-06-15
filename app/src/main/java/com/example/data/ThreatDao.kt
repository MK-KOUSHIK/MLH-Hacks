package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreatDao {
    @Query("SELECT * FROM threat_records ORDER BY timestamp DESC")
    fun getAllThreatsFlow(): Flow<List<ThreatEntity>>

    @Query("SELECT * FROM threat_records ORDER BY timestamp DESC")
    suspend fun getAllThreatsList(): List<ThreatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreat(threat: ThreatEntity): Long

    @Query("DELETE FROM threat_records WHERE id = :id")
    suspend fun deleteThreatById(id: Long)

    @Query("DELETE FROM threat_records")
    suspend fun clearAllThreats()

    @Query("SELECT COUNT(*) FROM threat_records")
    fun getScanCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM threat_records WHERE riskLevel != 'SAFE'")
    fun getThreatCountFlow(): Flow<Int>
}

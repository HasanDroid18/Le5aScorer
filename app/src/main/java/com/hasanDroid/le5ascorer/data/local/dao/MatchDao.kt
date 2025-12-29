package com.hasanDroid.le5ascorer.data.local.dao

import androidx.room.*
import com.hasanDroid.le5ascorer.data.local.entity.MatchEntity
import com.hasanDroid.le5ascorer.data.local.entity.MatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: MatchEntity): Long

    @Update
    suspend fun update(match: MatchEntity)

    @Delete
    suspend fun delete(match: MatchEntity)

    @Query("SELECT * FROM matches WHERE id = :matchId")
    suspend fun getMatchById(matchId: Long): MatchEntity?

    @Query("SELECT * FROM matches WHERE id = :matchId")
    fun observeMatchById(matchId: Long): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE status = :status ORDER BY createdAt DESC")
    fun getMatchesByStatus(status: MatchStatus): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches ORDER BY createdAt DESC")
    fun getAllMatches(): Flow<List<MatchEntity>>

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteById(matchId: Long)
}


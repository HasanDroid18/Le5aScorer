package com.hasandroid.le5ascorer.data.local.dao

import androidx.room.*
import com.hasandroid.le5ascorer.data.local.entity.RoundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(round: RoundEntity): Long

    @Update
    suspend fun update(round: RoundEntity)

    @Delete
    suspend fun delete(round: RoundEntity)

    @Query("SELECT * FROM rounds WHERE matchId = :matchId ORDER BY roundIndex ASC")
    fun getRoundsByMatchId(matchId: Long): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE matchId = :matchId ORDER BY roundIndex ASC")
    suspend fun getRoundsByMatchIdSync(matchId: Long): List<RoundEntity>

    @Query("SELECT * FROM rounds WHERE id = :roundId")
    suspend fun getRoundById(roundId: Long): RoundEntity?

    @Query("DELETE FROM rounds WHERE matchId = :matchId")
    suspend fun deleteByMatchId(matchId: Long)

    @Query("SELECT COUNT(*) FROM rounds WHERE matchId = :matchId")
    fun observeRoundCount(matchId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM rounds WHERE matchId = :matchId")
    suspend fun getRoundCount(matchId: Long): Int
}


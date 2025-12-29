package com.hasandroid.le5ascorer.data.local.dao

import androidx.room.*
import com.hasandroid.le5ascorer.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ScoreActionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actions: List<ScoreActionEntity>)

    @Query("SELECT * FROM score_actions WHERE roundId = :roundId")
    fun getActionsByRoundId(roundId: Long): Flow<List<ScoreActionEntity>>

    @Query("SELECT * FROM score_actions WHERE roundId = :roundId")
    suspend fun getActionsByRoundIdSync(roundId: Long): List<ScoreActionEntity>

    @Query("DELETE FROM score_actions WHERE roundId = :roundId")
    suspend fun deleteByRoundId(roundId: Long)

    @Query("SELECT * FROM score_actions WHERE roundId IN (:roundIds)")
    suspend fun getActionsByRoundIds(roundIds: List<Long>): List<ScoreActionEntity>

    @Query("SELECT * FROM score_actions")
    fun observeAllScoreActions(): Flow<List<ScoreActionEntity>>
}

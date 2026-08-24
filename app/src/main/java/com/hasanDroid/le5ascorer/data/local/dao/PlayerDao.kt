package com.hasanDroid.le5ascorer.data.local.dao

import androidx.room.*
import com.hasanDroid.le5ascorer.data.local.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule

@Dao
interface PlayerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: PlayerEntity): Long

    @Query("SELECT * FROM players WHERE id = :playerId")
    suspend fun getPlayerById(playerId: Long): PlayerEntity?

    @Query("SELECT * FROM players WHERE name LIKE '%' || :query || '%' ORDER BY lastUsedAt DESC LIMIT 10")
    fun searchPlayersByName(query: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY lastUsedAt DESC")
    fun getAllPlayers(): Flow<List<PlayerEntity>>

    /**
     * Names this game has actually used, most recent first.
     *
     * Tarneeb stores its two team names as player rows — the match table has
     * four player columns and no spare text column, and adding one would mean a
     * schema change this project cannot safely make. Without this filter those
     * team names would surface as suggestions when starting a Leekha match, and
     * vice versa. Scoping by scoreRule keeps each game's history to itself and
     * means Tarneeb remembers team names the same way Leekha remembers players.
     */
    @Query(
        """
        SELECT * FROM players WHERE id IN (
            SELECT player1Id FROM matches WHERE scoreRule = :scoreRule
            UNION SELECT player2Id FROM matches WHERE scoreRule = :scoreRule
            UNION SELECT player3Id FROM matches WHERE scoreRule = :scoreRule
            UNION SELECT player4Id FROM matches WHERE scoreRule = :scoreRule
        )
        ORDER BY lastUsedAt DESC
        """
    )
    fun getPlayersForRule(scoreRule: ScoreRule): Flow<List<PlayerEntity>>

    @Query("UPDATE players SET lastUsedAt = :timestamp WHERE id = :playerId")
    suspend fun updateLastUsed(playerId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM players WHERE name = :name LIMIT 1")
    suspend fun getPlayerByName(name: String): PlayerEntity?

    @Query("SELECT * FROM players ORDER BY lastUsedAt DESC")
    fun getAllPlayersSync(): List<PlayerEntity>
}


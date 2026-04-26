package com.hasanDroid.le5ascorer.data.local.dao

import androidx.room.*
import com.hasanDroid.le5ascorer.data.local.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE players SET lastUsedAt = :timestamp WHERE id = :playerId")
    suspend fun updateLastUsed(playerId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM players WHERE name = :name LIMIT 1")
    suspend fun getPlayerByName(name: String): PlayerEntity?

    @Query("SELECT * FROM players ORDER BY lastUsedAt DESC")
    fun getAllPlayersSync(): List<PlayerEntity>
}


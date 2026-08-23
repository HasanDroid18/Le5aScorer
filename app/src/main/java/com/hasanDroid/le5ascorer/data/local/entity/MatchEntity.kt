package com.hasanDroid.le5ascorer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val terminalScore: Int, // 51, 101, or 151
    val scoreRule: ScoreRule,
    val player1Id: Long,
    val player2Id: Long,
    val player3Id: Long,
    val player4Id: Long,
    val status: MatchStatus = MatchStatus.IN_PROGRESS,
    val loserImagePath: String? = null // Path to loser's photo
)


package com.hasanDroid.le5ascorer.domain.model

import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.data.local.entity.MatchStatus
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule

data class Player(
    val id: Long,
    val name: String
)

data class Match(
    val id: Long,
    val createdAt: Long,
    val completedAt: Long?,
    val terminalScore: Int,
    val scoreRule: ScoreRule,
    val players: List<Player>, // Always 4 players
    val status: MatchStatus,
    val roundCount: Int = 0,
    val leadingPlayerName: String? = null,
    val leadingScore: Int? = null
)

data class Round(
    val id: Long,
    val matchId: Long,
    val roundIndex: Int,
    val actions: List<ScoreAction> = emptyList()
)

data class ScoreAction(
    val id: Long = 0,
    val roundId: Long,
    val receiverIndex: Int,
    val actionType: ActionType,
    val delta: Int
)

data class PlayerScore(
    val playerIndex: Int,
    val playerName: String,
    val roundScore: Int,
    val cumulativeScore: Int
)

data class RoundScores(
    val roundIndex: Int,
    val playerScores: List<PlayerScore>
)

data class MatchDetail(
    val match: Match,
    val rounds: List<Round>,
    val scoreboard: List<RoundScores>
)


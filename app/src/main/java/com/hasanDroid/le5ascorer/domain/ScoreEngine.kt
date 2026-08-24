package com.hasanDroid.le5ascorer.domain

import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.domain.model.*

/**
 * Core scoring engine for Leekha card game.
 * Handles score calculation, cumulative totals, and winner detection.
 *
 * Leekha Scoring Rules:
 * - Bnt Baston (Queen of Spades): 13 points
 * - 10 Denare (Ten of Diamonds): 10 points
 * - 13 Hearts system: Each heart = 1 point, total 13 hearts
 * - Double Rule: ONLY set via DOUBLE button = 37 points for one player, 0 for others
 *   NOTE: Having both Bnt Baston + 10 Denare does NOT automatically create a double
 * - Normal round total: 13 + 10 + 13 = 36 points
 * - Double round total: 37 points (only via DOUBLE button)
 */
class ScoreEngine {

    companion object {
        const val BNT_BASTON_POINTS = 13
        const val TEN_DENARE_POINTS = 10
        const val HEART_POINTS = 1
        const val TOTAL_HEARTS = 13
        const val NORMAL_ROUND_TOTAL = 36
        const val DOUBLE_ROUND_TOTAL = 37
    }

    /**
     * Calculate scoreboard from match and rounds with Double rule support.
     */
    fun calculateScoreboard(
        match: Match,
        rounds: List<Round>,
        alternatePlayerOrder: Boolean = false
    ): List<RoundScores> {
        val playerNames = match.players.map { it.name }
        val cumulativeScores = IntArray(4) { 0 }
        val scoreboard = mutableListOf<RoundScores>()

        rounds.sortedBy { it.roundIndex }.forEach { round ->
            val roundScores = calculateRoundScores(round)

            // Update cumulative scores
            for (i in 0..3) {
                cumulativeScores[i] += roundScores[i]
            }

            // Create player scores for this round
            val playerScores = (0..3).map { index ->
                PlayerScore(
                    playerIndex = index,
                    playerName = playerNames.getOrNull(index) ?: "Player ${index + 1}",
                    roundScore = roundScores[index],
                    cumulativeScore = cumulativeScores[index]
                )
            }

            scoreboard.add(
                RoundScores(
                    roundIndex = round.roundIndex,
                    playerScores = playerScores
                )
            )
        }

        return scoreboard
    }

    /**
     * Calculate scores for a single round.
     * NOTE: Double is NOT automatically detected by having both cards.
     * Double is only set when the round was explicitly created with the DOUBLE button,
     * which results in one player having exactly 37 points.
     */
    fun calculateRoundScores(round: Round): IntArray {
        val roundScores = IntArray(4) { 0 }

        // Simply sum all actions for each player
        // If it's a double round, the actions will already reflect 37 for one player
        round.actions.forEach { action ->
            if (action.receiverIndex in 0..3) {
                roundScores[action.receiverIndex] += action.delta
            }
        }

        return roundScores
    }

    /**
     * Validate round scores according to Leekha rules.
     * IMPORTANT: Round total must be exactly 36 or 37 across ALL players
     * NOTE: Double is NOT automatically triggered by having both cards on same player.
     * It's only a double if the total is 37 (set explicitly via DOUBLE button).
     */
    fun validateRound(
        hasBntBaston: Int?,
        has10Denare: Int?,
        heartsDistribution: IntArray
    ): RoundValidation {
        val errors = mutableListOf<String>()

        // Calculate total round points
        val totalHearts = heartsDistribution.sum()
        var totalRoundPoints = 0

        // Add Bnt Baston if assigned
        if (hasBntBaston != null) {
            totalRoundPoints += BNT_BASTON_POINTS
        }

        // Add 10 Denare if assigned
        if (has10Denare != null) {
            totalRoundPoints += TEN_DENARE_POINTS
        }

        // Add all hearts
        totalRoundPoints += totalHearts

        // Check if it's a double round (total = 37)
        val isDouble = totalRoundPoints == DOUBLE_ROUND_TOTAL

        if (isDouble) {
            // Double round: total should be 37
            return RoundValidation(
                isValid = true,
                isDouble = true,
                totalPoints = DOUBLE_ROUND_TOTAL,
                totalHearts = totalHearts,
                errors = emptyList()
            )
        }

        // Normal round validation: Check ENTIRE ROUND total must be 36
        // Total = 13 (Bnt Baston) + 10 (10 Denare) + 13 (all hearts) = 36

        // Must have Bnt Baston assigned
        if (hasBntBaston == null) {
            errors.add("Bnt Baston must be assigned to one player")
        }

        // Must have 10 Denare assigned
        if (has10Denare == null) {
            errors.add("10 Denare must be assigned to one player")
        } else {
            totalRoundPoints += TEN_DENARE_POINTS
        }

        // Add all hearts
        totalRoundPoints += totalHearts

        // Validate hearts total
        if (totalHearts != TOTAL_HEARTS) {
            errors.add("Hearts must total exactly $TOTAL_HEARTS across all players (currently $totalHearts)")
        }

        // Validate TOTAL round points
        if (totalRoundPoints != NORMAL_ROUND_TOTAL) {
            errors.add("Round total must be $NORMAL_ROUND_TOTAL (Bnt Baston 13 + 10 Denare 10 + Hearts 13) - Currently: $totalRoundPoints")
        }

        return RoundValidation(
            isValid = errors.isEmpty(),
            isDouble = false,
            totalPoints = totalRoundPoints,
            totalHearts = totalHearts,
            errors = errors
        )
    }

    /**
     * Check if game is over (someone reached terminal score = they are LOSER)
     * Important: Reaching target score means you LOST, not won!
     *
     * Several players can cross on the same round, so every one of them is
     * reported rather than just the first found.
     */
    fun checkGameOver(scoreboard: List<RoundScores>, terminalScore: Int): GameOverResult? {
        if (scoreboard.isEmpty()) return null

        val reached = scoreboard.last().playerScores.filter { it.cumulativeScore >= terminalScore }
        if (reached.isEmpty()) return null

        return GameOverResult(
            loserIndices = reached.map { it.playerIndex },
            loserNames = reached.map { it.playerName },
            finalScores = scoreboard.last().playerScores.map { it.cumulativeScore }
        )
    }

    /**
     * Get the score value for an action type.
     * In Leekha: Hearts = 1 point each, Bnt Baston (Q♠) = 13 points, 10 Denare (10♦) = 10 points
     */
    fun getScoreForActionType(actionType: ActionType, count: Int = 1): Int {
        return when (actionType) {
            ActionType.HEART -> HEART_POINTS * count
            ActionType.Q_SPADES -> BNT_BASTON_POINTS * count
            ActionType.TEN_DIAMONDS -> TEN_DENARE_POINTS * count
            // Tarneeb rounds share this table but not this engine: their bid and
            // trick counts are not Leekha points and must never be summed as if
            // they were. TarneebScoreEngine reads them instead.
            ActionType.TARNEEB_BID, ActionType.TARNEEB_TRICKS -> 0
        }
    }
}

data class GameOverResult(
    val loserIndices: List<Int>,
    val loserNames: List<String>,
    val finalScores: List<Int>
)

data class RoundValidation(
    val isValid: Boolean,
    val isDouble: Boolean,
    val totalPoints: Int,
    val totalHearts: Int = 0,
    val errors: List<String>
)


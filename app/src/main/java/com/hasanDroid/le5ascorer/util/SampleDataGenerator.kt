package com.hasanDroid.le5ascorer.util

import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.model.ScoreAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random

/**
 * Sample data generator for testing the application.
 */
class SampleDataGenerator @Inject constructor(
    private val repository: LeekhaRepository
) {

    private val samplePlayerNames = listOf(
        "Hasan", "Mahdi", "Ali", "Hadi",
        "Omar", "Fatima", "Zahra", "Hassan",
        "Ahmed", "Sarah", "Layla", "Khalid"
    )

    /**
     * Generate sample matches with rounds for testing.
     */
    suspend fun generateSampleData(matchCount: Int = 3) = withContext(Dispatchers.IO) {
        repeat(matchCount) { index ->
            // Randomly select 4 players
            val players = samplePlayerNames.shuffled().take(4)

            // Random configuration
            val terminalScore = listOf(51, 101, 151).random()

            // Create match
            val matchId = repository.createMatch(
                player1Name = players[0],
                player2Name = players[1],
                player3Name = players[2],
                player4Name = players[3],
                terminalScore = terminalScore
            )

            // Add random rounds (3-8 rounds per match)
            val roundCount = Random.nextInt(3, 9)
            repeat(roundCount) {
                val actions = generateRandomRoundActions()
                repository.addRound(matchId, actions)
            }
        }
    }

    /**
     * Generate random score actions for a round.
     */
    private fun generateRandomRoundActions(): List<ScoreAction> {
        val actions = mutableListOf<ScoreAction>()

        // Distribute hearts (13 hearts total in a deck)
        var heartsRemaining = 13
        val heartDistribution = (0..3).map {
            val hearts = if (heartsRemaining > 0) {
                Random.nextInt(0, minOf(heartsRemaining + 1, 8))
            } else 0
            heartsRemaining -= hearts
            hearts
        }

        // One player gets Q♠ (13 points)
        val qSpadesReceiver = Random.nextInt(4)

        // One player gets 10♦ (-10 points)
        val tenDiamondsReceiver = Random.nextInt(4)

        // Create actions
        (0..3).forEach { playerIndex ->
            // Hearts
            if (heartDistribution[playerIndex] > 0) {
                actions.add(
                    ScoreAction(
                        roundId = 0,
                        receiverIndex = playerIndex,
                        actionType = ActionType.HEART,
                        delta = heartDistribution[playerIndex]
                    )
                )
            }

            // Q Spades
            if (playerIndex == qSpadesReceiver) {
                actions.add(
                    ScoreAction(
                        roundId = 0,
                        receiverIndex = playerIndex,
                        actionType = ActionType.Q_SPADES,
                        delta = 13
                    )
                )
            }

            // 10 Diamonds
            if (playerIndex == tenDiamondsReceiver) {
                actions.add(
                    ScoreAction(
                        roundId = 0,
                        receiverIndex = playerIndex,
                        actionType = ActionType.TEN_DIAMONDS,
                        delta = -10
                    )
                )
            }
        }

        return actions
    }

    /**
     * Clear all data from the database.
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        // Note: This would require additional repository methods
        // For now, this is a placeholder
    }
}


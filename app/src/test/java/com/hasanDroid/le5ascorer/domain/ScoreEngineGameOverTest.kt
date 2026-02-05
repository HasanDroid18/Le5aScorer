package com.hasanDroid.le5ascorer.domain

import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.domain.model.PlayerScore
import com.hasanDroid.le5ascorer.domain.model.RoundScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreEngineGameOverTest {

    private val engine = ScoreEngine()

    @Test
    fun `individual - no game over when all scores below terminal`() {
        val scoreboard = listOf(
            RoundScores(
                roundIndex = 1,
                playerScores = listOf(
                    PlayerScore(0, "A", 10, 10),
                    PlayerScore(1, "B", 10, 10),
                    PlayerScore(2, "C", 10, 10),
                    PlayerScore(3, "D", 6, 6)
                )
            )
        )

        val result = engine.checkGameOver(scoreboard, terminalScore = 101, scoreRule = ScoreRule.INDIVIDUAL)
        assertNull(result)
    }

    @Test
    fun `individual - game over when a player reaches exactly 101 (loser)`() {
        val scoreboard = listOf(
            RoundScores(
                roundIndex = 5,
                playerScores = listOf(
                    PlayerScore(0, "A", 0, 100),
                    PlayerScore(1, "B", 1, 101),
                    PlayerScore(2, "C", 0, 20),
                    PlayerScore(3, "D", 0, 30)
                )
            )
        )

        val result = engine.checkGameOver(scoreboard, terminalScore = 101, scoreRule = ScoreRule.INDIVIDUAL)
        assertNotNull(result)
        assertEquals(listOf(1), result!!.loserIndices)
        assertEquals(listOf("B"), result.loserNames)
        assertEquals(listOf(100, 101, 20, 30), result.finalScores)
    }

    @Test
    fun `individual - game over when a player exceeds 101 (loser)`() {
        val scoreboard = listOf(
            RoundScores(
                roundIndex = 6,
                playerScores = listOf(
                    PlayerScore(0, "A", 0, 102),
                    PlayerScore(1, "B", 0, 99),
                    PlayerScore(2, "C", 0, 25),
                    PlayerScore(3, "D", 0, 40)
                )
            )
        )

        val result = engine.checkGameOver(scoreboard, terminalScore = 101, scoreRule = ScoreRule.INDIVIDUAL)
        assertNotNull(result)
        assertEquals(listOf(0), result!!.loserIndices)
        assertEquals(listOf("A"), result.loserNames)
    }

    @Test
    fun `team - game over when team reaches 101 or more (loser)`() {
        // Team 1: players 0 + 2
        // Team 2: players 1 + 3
        val scoreboard = listOf(
            RoundScores(
                roundIndex = 3,
                playerScores = listOf(
                    PlayerScore(0, "A", 0, 60),
                    PlayerScore(1, "B", 0, 40),
                    PlayerScore(2, "C", 0, 41), // team1 = 101
                    PlayerScore(3, "D", 0, 10)  // team2 = 50
                )
            )
        )

        val result = engine.checkGameOver(scoreboard, terminalScore = 101, scoreRule = ScoreRule.TEAM)
        assertNotNull(result)
        assertEquals(listOf(0, 2), result!!.loserIndices)
        assertEquals(listOf("A", "C"), result.loserNames)
        assertEquals(listOf(101, 50), result.finalScores)
    }
}

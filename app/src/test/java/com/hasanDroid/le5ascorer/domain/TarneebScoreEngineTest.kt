package com.hasanDroid.le5ascorer.domain

import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.domain.model.Round
import com.hasanDroid.le5ascorer.domain.model.ScoreAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every row of the Tarneeb scoring table, plus the win condition.
 *
 * This is the only place the rules are actually verified: the app cannot be
 * built or run in the environment it is developed in, so these run in CI
 * without an emulator and are what stands between a rules mistake and release.
 */
class TarneebScoreEngineTest {

    private val engine = TarneebScoreEngine()

    // ================= making the bid =================

    @Test
    fun `bidder that makes its bid scores the tricks it took, not the bid`() {
        // Bid 7, took 9 — the extra two count.
        val points = engine.scoreRound(bidderTeam = 0, bid = 7, tricks = intArrayOf(9, 4))
        assertEquals(9, points[0])
    }

    @Test
    fun `defenders score nothing when the bid is made`() {
        val points = engine.scoreRound(bidderTeam = 0, bid = 7, tricks = intArrayOf(9, 4))
        assertEquals(0, points[1])
    }

    @Test
    fun `bid met exactly still counts as made`() {
        val points = engine.scoreRound(bidderTeam = 1, bid = 8, tricks = intArrayOf(5, 8))
        assertEquals(8, points[1])
        assertEquals(0, points[0])
    }

    // ================= failing the bid =================

    @Test
    fun `bidder that falls short loses the bid it declared, not the tricks it took`() {
        // Bid 9, took 7. The penalty is the promise, so -9 rather than -7.
        val points = engine.scoreRound(bidderTeam = 0, bid = 9, tricks = intArrayOf(7, 6))
        assertEquals(-9, points[0])
    }

    @Test
    fun `defenders score their tricks when the bid fails`() {
        val points = engine.scoreRound(bidderTeam = 0, bid = 9, tricks = intArrayOf(7, 6))
        assertEquals(6, points[1])
    }

    @Test
    fun `missing by one still costs the whole bid`() {
        val points = engine.scoreRound(bidderTeam = 1, bid = 10, tricks = intArrayOf(4, 9))
        assertEquals(-10, points[1])
        assertEquals(4, points[0])
    }

    // ================= thirteen tricks =================

    @Test
    fun `kabbout declared and delivered pays 26`() {
        val points = engine.scoreRound(bidderTeam = 0, bid = 13, tricks = intArrayOf(13, 0))
        assertEquals(26, points[0])
        assertEquals(0, points[1])
    }

    @Test
    fun `kabbout declared and missed costs 16, not 13`() {
        val points = engine.scoreRound(bidderTeam = 0, bid = 13, tricks = intArrayOf(12, 1))
        assertEquals(-16, points[0])
        assertEquals(1, points[1])
    }

    @Test
    fun `sweeping all 13 on a smaller bid pays the 16 bonus, not 13`() {
        val points = engine.scoreRound(bidderTeam = 0, bid = 9, tricks = intArrayOf(13, 0))
        assertEquals(16, points[0])
        assertEquals(0, points[1])
    }

    @Test
    fun `defenders who sweep all 13 are paid the bonus too`() {
        // The bidder promised 7 and took none; the defenders took everything.
        val points = engine.scoreRound(bidderTeam = 0, bid = 7, tricks = intArrayOf(0, 13))
        assertEquals(-7, points[0])
        assertEquals(16, points[1])
    }

    // ================= scoreboard accumulation =================

    @Test
    fun `scoreboard accumulates running totals across rounds`() {
        val rounds = listOf(
            round(0, bidder = 0, bid = 7, tricks = intArrayOf(8, 5)),
            round(1, bidder = 1, bid = 9, tricks = intArrayOf(6, 7))
        )

        val results = engine.calculateScoreboard(rounds)

        assertEquals(2, results.size)
        // Round 1: team 0 made 7 with 8 tricks.
        assertEquals(listOf(8, 0), results[0].cumulative)
        // Round 2: team 1 bid 9 and took 7, so -9; team 0 banks its 6.
        assertEquals(listOf(6, -9), results[1].roundPoints)
        assertEquals(listOf(14, -9), results[1].cumulative)
    }

    @Test
    fun `scoreboard reports whether each round's bid was made`() {
        val results = engine.calculateScoreboard(
            listOf(
                round(0, bidder = 0, bid = 7, tricks = intArrayOf(8, 5)),
                round(1, bidder = 0, bid = 9, tricks = intArrayOf(7, 6))
            )
        )
        assertTrue(results[0].bidMade)
        assertFalse(results[1].bidMade)
    }

    @Test
    fun `a round with no bid recorded is skipped rather than scored as zero`() {
        val incomplete = Round(
            id = 1, matchId = 1, roundIndex = 0,
            actions = listOf(
                ScoreAction(roundId = 1, receiverIndex = 0, actionType = ActionType.TARNEEB_TRICKS, delta = 7)
            )
        )
        assertTrue(engine.calculateScoreboard(listOf(incomplete)).isEmpty())
    }

    @Test
    fun `totals can go negative and recover`() {
        val results = engine.calculateScoreboard(
            listOf(
                round(0, bidder = 0, bid = 10, tricks = intArrayOf(3, 10)),
                round(1, bidder = 0, bid = 7, tricks = intArrayOf(9, 4))
            )
        )
        assertEquals(-10, results[0].cumulative[0])
        assertEquals(-1, results[1].cumulative[0])
    }

    // ================= winning =================

    @Test
    fun `no winner while both teams are short of the target`() {
        val results = engine.calculateScoreboard(
            listOf(round(0, bidder = 0, bid = 7, tricks = intArrayOf(9, 4)))
        )
        assertNull(engine.checkGameWon(results, terminalScore = 31, teamNames = NAMES))
    }

    @Test
    fun `reaching the target wins, unlike Leekha where it loses`() {
        val results = engine.calculateScoreboard(
            listOf(
                round(0, bidder = 0, bid = 11, tricks = intArrayOf(11, 2)),
                round(1, bidder = 0, bid = 11, tricks = intArrayOf(11, 2)),
                round(2, bidder = 0, bid = 9, tricks = intArrayOf(9, 4))
            )
        )

        val result = engine.checkGameWon(results, terminalScore = 31, teamNames = NAMES)

        assertEquals(0, result?.winnerIndex)
        assertEquals("Us", result?.winnerName)
        assertEquals("Them", result?.loserName)
    }

    @Test
    fun `the team with the higher total wins even if both are past the target`() {
        // Constructed rather than played: guards the tie-break if scoring ever
        // changes so both teams can cross in one round.
        val results = listOf(
            TarneebRoundResult(
                roundIndex = 0, bidderTeam = 0, bid = 7,
                tricks = listOf(7, 6), roundPoints = listOf(35, 40),
                cumulative = listOf(35, 40)
            )
        )
        val result = engine.checkGameWon(results, terminalScore = 31, teamNames = NAMES)
        assertEquals(1, result?.winnerIndex)
        assertEquals("Them", result?.winnerName)
    }

    @Test
    fun `no winner before any round is played`() {
        assertNull(engine.checkGameWon(emptyList(), terminalScore = 31, teamNames = NAMES))
    }

    // ================= round decoding =================

    @Test
    fun `readRound recovers exactly what was stored`() {
        val entry = engine.readRound(
            actionsFor(bidder = 1, bid = 10, tricks = intArrayOf(3, 10))
        )
        assertEquals(1, entry?.bidderTeam)
        assertEquals(10, entry?.bid)
        assertEquals(listOf(3, 10), entry?.tricks?.toList())
    }

    // ================= helpers =================

    private fun actionsFor(bidder: Int, bid: Int, tricks: IntArray) = listOf(
        ScoreAction(roundId = 1, receiverIndex = bidder, actionType = ActionType.TARNEEB_BID, delta = bid),
        ScoreAction(roundId = 1, receiverIndex = 0, actionType = ActionType.TARNEEB_TRICKS, delta = tricks[0]),
        ScoreAction(roundId = 1, receiverIndex = 1, actionType = ActionType.TARNEEB_TRICKS, delta = tricks[1])
    )

    private fun round(index: Int, bidder: Int, bid: Int, tricks: IntArray) = Round(
        id = index + 1L,
        matchId = 1,
        roundIndex = index,
        actions = actionsFor(bidder, bid, tricks)
    )

    private companion object {
        val NAMES = listOf("Us", "Them")
    }
}

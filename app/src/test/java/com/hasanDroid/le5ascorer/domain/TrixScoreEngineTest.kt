package com.hasanDroid.le5ascorer.domain

import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.domain.TrixContractType.DIAMONDS
import com.hasanDroid.le5ascorer.domain.TrixContractType.KING
import com.hasanDroid.le5ascorer.domain.TrixContractType.LTOOSH
import com.hasanDroid.le5ascorer.domain.TrixContractType.QUEENS
import com.hasanDroid.le5ascorer.domain.TrixContractType.TRIX
import com.hasanDroid.le5ascorer.domain.model.Round
import com.hasanDroid.le5ascorer.domain.model.ScoreAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Trix rules as the user's rule sheet states them, plus a replay of the
 * reference game from the iOS screenshots (IMG_7284–7297), whose running totals
 * were checked by hand: −150/75 → −175/0 → −235/−135 → −35/165 → −85/85.
 *
 * Seats in that game: 0 Hasan, 1 Hsen (Team A), 2 Ali, 3 Meso (Team B).
 */
class TrixScoreEngineTest {

    private val engine = TrixScoreEngine()

    // The reference kingdom, in the order it was played.
    private val screenshotKingdom = listOf(
        TrixContract.King(team = 0, doubled = true),
        TrixContract.Queens(
            listOf(
                QueenTake(team = 1, doubled = true),  // ♠
                QueenTake(team = 0, doubled = false), // ♥
                QueenTake(team = 1, doubled = true),  // ♦
                QueenTake(team = 0, doubled = true)   // ♣
            )
        ),
        TrixContract.Ltoosh(listOf(4, 9)),
        TrixContract.Trix(listOf(4, 2, 1, 3)),
        TrixContract.Diamonds(listOf(5, 8))
    )

    // A kingdom that leaves both teams on exactly zero.
    private val levelKingdom = listOf(
        TrixContract.King(team = 0, doubled = false),
        TrixContract.Queens(
            listOf(
                QueenTake(0, false), QueenTake(0, false),
                QueenTake(1, false), QueenTake(1, false)
            )
        ),
        TrixContract.Diamonds(listOf(5, 8)),
        TrixContract.Ltoosh(listOf(5, 8)),
        TrixContract.Trix(listOf(1, 4, 2, 3))
    )

    private fun rounds(contracts: List<TrixContract>): List<Round> =
        contracts.mapIndexed { index, contract ->
            Round(
                id = index + 100L,
                matchId = 1,
                roundIndex = index,
                actions = engine.toActions(contract)
            )
        }

    // ================= scoring each contract =================

    @Test
    fun `king taker loses 75`() {
        assertEquals(listOf(-75, 0), engine.scoreContract(TrixContract.King(0, false), doubling = true))
    }

    @Test
    fun `doubled king costs the taker 150 and pays the other team 75`() {
        assertEquals(listOf(-150, 75), engine.scoreContract(TrixContract.King(0, true), doubling = true))
        assertEquals(listOf(75, -150), engine.scoreContract(TrixContract.King(1, true), doubling = true))
    }

    @Test
    fun `doubled flags are ignored when doubling is off`() {
        assertEquals(listOf(-75, 0), engine.scoreContract(TrixContract.King(0, true), doubling = false))
        val queens = TrixContract.Queens(List(4) { QueenTake(1, doubled = true) })
        assertEquals(listOf(0, -100), engine.scoreContract(queens, doubling = false))
    }

    @Test
    fun `queens score per queen with doubling per queen`() {
        assertEquals(listOf(-25, -75), engine.scoreContract(screenshotKingdom[1], doubling = true))
    }

    @Test
    fun `diamonds cost 10 each and ltoosh 15 per trick`() {
        assertEquals(listOf(-50, -80), engine.scoreContract(TrixContract.Diamonds(listOf(5, 8)), true))
        assertEquals(listOf(-60, -135), engine.scoreContract(TrixContract.Ltoosh(listOf(4, 9)), true))
    }

    @Test
    fun `trix pays each player's place to their team`() {
        // Hasan 4th +50, Hsen 2nd +150 → 200; Ali 1st +200, Meso 3rd +100 → 300.
        assertEquals(listOf(200, 300), engine.scoreContract(TrixContract.Trix(listOf(4, 2, 1, 3)), true))
    }

    @Test
    fun `a complete kingdom sums to zero, doubled or not`() {
        listOf(true, false).forEach { doubling ->
            val sum = screenshotKingdom.sumOf { engine.scoreContract(it, doubling).sum() }
            assertEquals(0, sum)
        }
    }

    // ================= the reference game =================

    @Test
    fun `screenshot game replays to the totals shown`() {
        val setup = TrixSetup(openerSeat = 2, doubling = true)
        val board = engine.calculateScoreboard(rounds(screenshotKingdom), setup)

        assertEquals(
            listOf(listOf(-150, 75), listOf(-175, 0), listOf(-235, -135), listOf(-35, 165), listOf(-85, 85)),
            board.results.map { it.totals }
        )
        assertEquals(listOf(-85, 85), board.totals)
        // Kingdom 1 was Ali's; kingdom 2 opens on Hsen with nothing played.
        assertEquals(2, board.results.first().ownerSeat)
        assertEquals(1, board.kingdom)
        assertEquals(1, board.ownerSeat)
        assertTrue(board.playedInKingdom.isEmpty())
        assertFalse(board.isGameOver)
        assertNull(board.winnerTeam)
    }

    @Test
    fun `contracts played so far in the current kingdom are reported`() {
        val board = engine.calculateScoreboard(rounds(screenshotKingdom.take(2)), TrixSetup(2, true))
        assertEquals(setOf(KING, QUEENS), board.playedInKingdom)
        assertEquals(0, board.kingdom)
    }

    // ================= rotation and setup =================

    @Test
    fun `ownership rotates P1 P3 P2 P4 from the 7 of hearts holder`() {
        fun order(opener: Int) = (0 until 4).map { engine.ownerSeat(TrixSetup(opener, false), it) }
        assertEquals(listOf(0, 2, 1, 3), order(0))
        assertEquals(listOf(1, 3, 0, 2), order(1))
        assertEquals(listOf(2, 1, 3, 0), order(2))
        assertEquals(listOf(3, 0, 2, 1), order(3))
    }

    @Test
    fun `setup survives the trip through terminalScore`() {
        for (seat in 0..3) for (doubling in listOf(true, false)) {
            val setup = TrixSetup(seat, doubling)
            assertEquals(setup, TrixSetup.decode(setup.encode()))
        }
    }

    // ================= storage =================

    @Test
    fun `every contract type survives the trip through actions`() {
        screenshotKingdom.forEach { contract ->
            assertEquals(contract, engine.readRound(engine.toActions(contract)))
        }
    }

    @Test
    fun `readRound rejects diamonds that do not account for all 13`() {
        assertNull(engine.readRound(engine.toActions(TrixContract.Diamonds(listOf(5, 7)))))
    }

    @Test
    fun `readRound rejects a finishing order with a repeated place`() {
        assertNull(engine.readRound(engine.toActions(TrixContract.Trix(listOf(1, 1, 2, 3)))))
    }

    @Test
    fun `readRound rejects mixed and empty rounds`() {
        val mixed = engine.toActions(TrixContract.King(0, false)) +
            engine.toActions(TrixContract.Ltoosh(listOf(6, 7)))
        assertNull(engine.readRound(mixed))
        assertNull(engine.readRound(emptyList()))
    }

    @Test
    fun `an unreadable round is skipped without shifting kingdoms`() {
        val broken = Round(id = 99, matchId = 1, roundIndex = 2, actions = listOf(
            ScoreAction(roundId = 99, receiverIndex = 0, actionType = ActionType.TRIX_LTOOSH, delta = 3)
        ))
        val valid = rounds(screenshotKingdom.take(2))
        val board = engine.calculateScoreboard(valid + broken, TrixSetup(2, true))
        assertEquals(2, board.results.size)
        assertEquals(setOf(KING, QUEENS), board.playedInKingdom)
    }

    // ================= game over =================

    @Test
    fun `max swings match the rule sheet`() {
        assertEquals(listOf(75, 100, 130, 195, 200), TrixContractType.entries.map { engine.maxSwing(it, false) })
        assertEquals(listOf(225, 300, 130, 195, 200), TrixContractType.entries.map { engine.maxSwing(it, true) })
    }

    @Test
    fun `early game over is never checked in the first two kingdoms`() {
        val played = List(2) { TrixContractType.entries }.flatten() // 10 contracts
        assertFalse(engine.isGameOver(listOf(-5000, 5000), played, doubling = false))
    }

    @Test
    fun `gap equal to the remaining ceiling continues, one more ends it`() {
        // 10 played, then King opens kingdom 3. Remaining: 4 contracts here
        // (100 + 130 + 195 + 200 = 625) plus a full kingdom 4 (700) = 1325.
        val played = List(2) { TrixContractType.entries }.flatten() + KING
        assertEquals(1325, engine.remainingSwing(played, doubling = false))
        assertFalse(engine.isGameOver(listOf(0, 1325), played, doubling = false))
        assertTrue(engine.isGameOver(listOf(0, 1326), played, doubling = false))
        assertTrue(engine.isGameOver(listOf(1326, 0), played, doubling = false))
    }

    @Test
    fun `doubling raises the remaining ceiling`() {
        val played = List(2) { TrixContractType.entries }.flatten() + KING
        // 300 + 130 + 195 + 200 = 825, plus 1050 for kingdom 4.
        assertEquals(1875, engine.remainingSwing(played, doubling = true))
    }

    @Test
    fun `isGameOver is recomputed from current totals only`() {
        val played = List(2) { TrixContractType.entries }.flatten() + KING
        assertTrue(engine.isGameOver(listOf(0, 2000), played, false))
        assertFalse(engine.isGameOver(listOf(0, 100), played, false))
    }

    @Test
    fun `twenty contracts end the game and higher total wins`() {
        val board = engine.calculateScoreboard(rounds(List(4) { screenshotKingdom }.flatten()), TrixSetup(2, true))
        assertTrue(board.isGameOver)
        assertEquals(listOf(-340, 340), board.totals)
        assertEquals(1, board.winnerTeam)
        assertEquals(4, board.kingdom)
    }

    @Test
    fun `equal totals after twenty contracts is a draw`() {
        val board = engine.calculateScoreboard(rounds(List(4) { levelKingdom }.flatten()), TrixSetup(0, false))
        assertTrue(board.isGameOver)
        assertEquals(listOf(0, 0), board.totals)
        assertNull(board.winnerTeam)
    }

    @Test
    fun `contract type order in the enum is the screen order`() {
        assertEquals(listOf(KING, QUEENS, DIAMONDS, LTOOSH, TRIX), TrixContractType.entries.toList())
    }
}

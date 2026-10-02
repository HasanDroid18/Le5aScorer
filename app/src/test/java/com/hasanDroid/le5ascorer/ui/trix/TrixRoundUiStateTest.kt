package com.hasanDroid.le5ascorer.ui.trix

import com.hasanDroid.le5ascorer.domain.QueenTake
import com.hasanDroid.le5ascorer.domain.TrixContract
import com.hasanDroid.le5ascorer.domain.TrixContractType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrixRoundUiStateTest {

    private fun state(type: TrixContractType, doubling: Boolean = true) =
        TrixRoundUiState(type = type, doubling = doubling)

    @Test
    fun `king is incomplete until a team is chosen`() {
        val s = state(TrixContractType.KING)
        assertNull(s.toContract())
        assertEquals(TrixContract.King(1, true), s.copy(kingTeam = 1, kingDoubled = true).toContract())
    }

    @Test
    fun `doubled answers are dropped when doubling is off`() {
        val s = state(TrixContractType.KING, doubling = false).copy(kingTeam = 0, kingDoubled = true)
        assertEquals(TrixContract.King(0, false), s.toContract())
    }

    @Test
    fun `queens are incomplete until all four are assigned`() {
        val partial = state(TrixContractType.QUEENS).copy(queenTeams = listOf(0, 1, null, 0))
        assertNull(partial.toContract())
        val full = partial.copy(queenTeams = listOf(0, 1, 1, 0), queenDoubled = listOf(false, true, false, false))
        assertEquals(
            TrixContract.Queens(listOf(QueenTake(0, false), QueenTake(1, true), QueenTake(1, false), QueenTake(0, false))),
            full.toContract()
        )
    }

    @Test
    fun `counts take from the pool and stop at thirteen`() {
        var s = state(TrixContractType.DIAMONDS)
        repeat(20) { s = s.withCountChange(0, +1) }
        assertEquals(listOf(13, 0), s.counts)
        assertEquals(TrixContract.Diamonds(listOf(13, 0)), s.toContract())
    }

    @Test
    fun `adding to a full split takes from the other team`() {
        val s = state(TrixContractType.LTOOSH).copy(counts = listOf(4, 9)).withCountChange(0, +1)
        assertEquals(listOf(5, 8), s.counts)
    }

    @Test
    fun `removing never goes below zero and leaves the other team alone`() {
        val s = state(TrixContractType.LTOOSH).copy(counts = listOf(0, 13)).withCountChange(0, -1)
        assertEquals(listOf(0, 13), s.counts)
        val t = s.withCountChange(1, -1)
        assertEquals(listOf(0, 12), t.counts)
        assertNull(t.toContract())
    }

    @Test
    fun `picking a taken place moves it`() {
        val s = state(TrixContractType.TRIX).withPlace(0, 1).withPlace(1, 1)
        assertEquals(listOf(null, 1, null, null), s.places)
        assertNull(s.toContract())
    }

    @Test
    fun `trix completes once every seat has a distinct place`() {
        val s = state(TrixContractType.TRIX)
            .withPlace(0, 4).withPlace(1, 2).withPlace(2, 1).withPlace(3, 3)
        assertEquals(TrixContract.Trix(listOf(4, 2, 1, 3)), s.toContract())
    }

    @Test
    fun `a stored contract loads back into the same answers`() {
        val contracts = listOf(
            TrixContract.King(1, true),
            TrixContract.Queens(listOf(QueenTake(1, true), QueenTake(0, false), QueenTake(1, true), QueenTake(0, true))),
            TrixContract.Diamonds(listOf(5, 8)),
            TrixContract.Ltoosh(listOf(4, 9)),
            TrixContract.Trix(listOf(4, 2, 1, 3))
        )
        contracts.forEach { contract ->
            assertEquals(contract, state(contract.type).withContract(contract).toContract())
        }
    }

    @Test
    fun `team names join partners`() {
        val s = TrixRoundUiState(playerNames = listOf("Hasan", "Hsen", "Ali", "Meso"))
        assertEquals(listOf("Hasan & Hsen", "Ali & Meso"), s.teamNames)
    }
}

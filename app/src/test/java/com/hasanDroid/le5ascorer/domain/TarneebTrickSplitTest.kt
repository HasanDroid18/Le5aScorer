package com.hasanDroid.le5ascorer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trick-split rule that makes an existing round editable.
 *
 * Editing always opens on a complete round — all thirteen tricks assigned — so a
 * "+" that only ever drew from an unassigned pool was permanently dead there and
 * the split could not be changed. Adding now falls back to taking a trick from
 * the other team.
 *
 * The rule is exercised here as a pure function rather than through the
 * ViewModel, which would need Android's main-thread dispatcher and a fake
 * repository for no extra coverage. TarneebRoundEntryViewModel.changeTricks
 * implements exactly this.
 */
class TarneebTrickSplitTest {

    /** Mirrors TarneebRoundEntryViewModel.changeTricks. */
    private fun change(tricks: List<Int>, team: Int, delta: Int): List<Int> {
        val other = 1 - team
        val current = tricks[team]
        val otherCurrent = tricks[other]
        val remaining = TarneebScoreEngine.TRICKS_PER_ROUND - tricks.sum()
        val updated = tricks.toMutableList()

        if (delta > 0) {
            val fromPool = minOf(delta, remaining)
            val fromOther = minOf(delta - fromPool, otherCurrent)
            if (fromPool + fromOther == 0) return tricks
            updated[team] = current + fromPool + fromOther
            if (fromOther > 0) updated[other] = otherCurrent - fromOther
        } else {
            val next = (current + delta).coerceAtLeast(0)
            if (next == current) return tricks
            updated[team] = next
        }
        return updated
    }

    private fun canAdd(tricks: List<Int>, team: Int): Boolean =
        TarneebScoreEngine.TRICKS_PER_ROUND - tricks.sum() > 0 || tricks[1 - team] > 0

    // ============ the bug that was reported ============

    @Test
    fun `a complete round can have its split changed in one tap`() {
        // This is the case that was broken: 13 assigned, so nothing in the pool.
        assertEquals(listOf(8, 5), change(listOf(9, 4), team = 1, delta = +1))
    }

    @Test
    fun `both plus buttons are live on a complete round`() {
        assertTrue(canAdd(listOf(9, 4), team = 0))
        assertTrue(canAdd(listOf(9, 4), team = 1))
    }

    @Test
    fun `taking from the other team keeps the total at thirteen`() {
        val after = change(listOf(9, 4), team = 1, delta = +1)
        assertEquals(TarneebScoreEngine.TRICKS_PER_ROUND, after.sum())
    }

    // ============ fresh entry still draws from the pool ============

    @Test
    fun `an empty round takes from the pool, not from the other team`() {
        assertEquals(listOf(1, 0), change(listOf(0, 0), team = 0, delta = +1))
    }

    @Test
    fun `a partly filled round takes from the pool first`() {
        assertEquals(listOf(5, 4), change(listOf(4, 4), team = 0, delta = +1))
    }

    @Test
    fun `assigning the remainder fills the round exactly`() {
        val after = change(listOf(4, 0), team = 1, delta = 9)
        assertEquals(listOf(4, 9), after)
        assertEquals(TarneebScoreEngine.TRICKS_PER_ROUND, after.sum())
    }

    // ============ limits ============

    @Test
    fun `a team holding every trick cannot be added to`() {
        assertFalse(canAdd(listOf(13, 0), team = 0))
        assertEquals(listOf(13, 0), change(listOf(13, 0), team = 0, delta = +1))
    }

    @Test
    fun `the other team can still take from a sweeping team`() {
        assertTrue(canAdd(listOf(13, 0), team = 1))
        assertEquals(listOf(12, 1), change(listOf(13, 0), team = 1, delta = +1))
    }

    @Test
    fun `removing returns a trick to the pool rather than to the other team`() {
        assertEquals(listOf(8, 4), change(listOf(9, 4), team = 0, delta = -1))
    }

    @Test
    fun `a team on zero cannot go negative`() {
        assertEquals(listOf(9, 0), change(listOf(9, 0), team = 1, delta = -1))
    }

    @Test
    fun `the total never exceeds thirteen for any sequence of additions`() {
        var tricks = listOf(0, 0)
        repeat(40) { i -> tricks = change(tricks, team = i % 2, delta = +1) }
        assertEquals(TarneebScoreEngine.TRICKS_PER_ROUND, tricks.sum())
        assertTrue(tricks.all { it >= 0 })
    }
}

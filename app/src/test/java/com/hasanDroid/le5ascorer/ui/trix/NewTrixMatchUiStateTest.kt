package com.hasanDroid.le5ascorer.ui.trix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTrixMatchUiStateTest {

    private val full = NewTrixMatchUiState(names = listOf("Hasan", "Hsen", "Ali", "Meso"))

    @Test
    fun `four distinct names complete the setup`() {
        assertTrue(full.isComplete)
        assertTrue(full.canStart)
    }

    @Test
    fun `a blank seat blocks the start`() {
        val s = full.copy(names = listOf("Hasan", " ", "Ali", "Meso"))
        assertFalse(s.isComplete)
        assertFalse(s.canStart)
    }

    @Test
    fun `the same name twice is a duplicate whatever the case`() {
        val s = full.copy(names = listOf("Hasan", "hasan ", "Ali", "Meso"))
        assertTrue(s.hasDuplicateNames)
        assertFalse(s.isComplete)
    }

    @Test
    fun `the opener step needs a 7 of hearts holder`() {
        val choosing = full.copy(choosingOpener = true)
        assertFalse(choosing.canStart)
        assertTrue(choosing.copy(openerSeat = 2).canStart)
        assertFalse(choosing.copy(openerSeat = 2, isCreating = true).canStart)
    }
}

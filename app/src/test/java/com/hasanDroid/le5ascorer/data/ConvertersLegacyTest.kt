package com.hasanDroid.le5ascorer.data

import com.hasanDroid.le5ascorer.data.local.Converters
import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the promise that adding a second game costs nobody their saved matches.
 *
 * Both enum columns are TEXT written through these converters, and the database
 * is built with fallbackToDestructiveMigration() behind a catch block that
 * *deletes* the database on failure. So a converter that throws on a value it
 * does not recognise does not degrade — it wipes data. These tests pin the
 * leniency in place so it cannot be refactored away.
 */
class ConvertersLegacyTest {

    private val converters = Converters()

    @Test
    fun `matches written before team scoring was removed still load`() {
        assertEquals(ScoreRule.INDIVIDUAL, converters.toScoreRule("TEAM"))
    }

    @Test
    fun `an unrecognised score rule falls back to Leekha rather than throwing`() {
        assertEquals(ScoreRule.INDIVIDUAL, converters.toScoreRule("SOME_FUTURE_GAME"))
    }

    @Test
    fun `both game modes round-trip`() {
        ScoreRule.values().forEach { rule ->
            assertEquals(rule, converters.toScoreRule(converters.fromScoreRule(rule)))
        }
    }

    @Test
    fun `an unrecognised action type does not throw`() {
        assertEquals(ActionType.HEART, converters.toActionType("SOME_FUTURE_CARD"))
    }

    @Test
    fun `every action type round-trips, including the Tarneeb ones`() {
        ActionType.values().forEach { type ->
            assertEquals(type, converters.toActionType(converters.fromActionType(type)))
        }
    }
}

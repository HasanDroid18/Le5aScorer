package com.hasanDroid.le5ascorer.data.local

import androidx.room.TypeConverter
import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.data.local.entity.MatchStatus
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule

class Converters {

    @TypeConverter
    fun fromScoreRule(value: ScoreRule): String {
        return value.name
    }

    /**
     * Tolerates values no longer in the enum. Matches created before team
     * scoring was removed still hold "TEAM" in this column, and a bare
     * valueOf() would throw IllegalArgumentException and take down the match
     * list for anyone who ever created one.
     */
    @TypeConverter
    fun toScoreRule(value: String): ScoreRule =
        runCatching { ScoreRule.valueOf(value) }.getOrDefault(ScoreRule.INDIVIDUAL)

    @TypeConverter
    fun fromMatchStatus(value: MatchStatus): String {
        return value.name
    }

    @TypeConverter
    fun toMatchStatus(value: String): MatchStatus {
        return MatchStatus.valueOf(value)
    }

    @TypeConverter
    fun fromActionType(value: ActionType): String {
        return value.name
    }

    /**
     * Lenient for the same reason toScoreRule is: a bare valueOf() throws on any
     * value the current build does not know, and this converter runs while
     * loading the match list, so one unrecognised row would take the whole
     * screen down rather than degrade. HEART is the safe default — it is worth
     * one point, so a stray row skews a score rather than crashing the app.
     */
    @TypeConverter
    fun toActionType(value: String): ActionType =
        runCatching { ActionType.valueOf(value) }.getOrDefault(ActionType.HEART)
}


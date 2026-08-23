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

    @TypeConverter
    fun toActionType(value: String): ActionType {
        return ActionType.valueOf(value)
    }
}


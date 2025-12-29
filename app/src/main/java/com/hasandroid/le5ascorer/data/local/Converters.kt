package com.hasandroid.le5ascorer.data.local

import androidx.room.TypeConverter
import com.hasandroid.le5ascorer.data.local.entity.ActionType
import com.hasandroid.le5ascorer.data.local.entity.MatchStatus
import com.hasandroid.le5ascorer.data.local.entity.ScoreRule

class Converters {

    @TypeConverter
    fun fromScoreRule(value: ScoreRule): String {
        return value.name
    }

    @TypeConverter
    fun toScoreRule(value: String): ScoreRule {
        return ScoreRule.valueOf(value)
    }

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


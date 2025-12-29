package com.hasanDroid.le5ascorer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hasanDroid.le5ascorer.data.local.dao.*
import com.hasanDroid.le5ascorer.data.local.entity.*

@Database(
    entities = [
        PlayerEntity::class,
        MatchEntity::class,
        RoundEntity::class,
        ScoreActionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LeekhaDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun roundDao(): RoundDao
    abstract fun scoreActionDao(): ScoreActionDao
}


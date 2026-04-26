package com.hasanDroid.le5ascorer.di

import android.content.Context
import androidx.room.Room
import com.hasanDroid.le5ascorer.data.local.LeekhaDatabase
import com.hasanDroid.le5ascorer.data.local.dao.*
import com.hasanDroid.le5ascorer.domain.ScoreEngine
import com.hasanDroid.le5ascorer.util.VersionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLeekhaDatabase(@ApplicationContext context: Context): LeekhaDatabase {
        return Room.databaseBuilder(
            context,
            LeekhaDatabase::class.java,
            "leekha_database"
        )
        // If a migration is needed in the future, add it here:
        // .addMigrations(MIGRATION_1_2)
        // For schema compatibility issues, allow destructive migration as last resort
        // (with proper data backup via backup_rules.xml)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun providePlayerDao(database: LeekhaDatabase): PlayerDao {
        return database.playerDao()
    }

    @Provides
    @Singleton
    fun provideMatchDao(database: LeekhaDatabase): MatchDao {
        return database.matchDao()
    }

    @Provides
    @Singleton
    fun provideRoundDao(database: LeekhaDatabase): RoundDao {
        return database.roundDao()
    }

    @Provides
    @Singleton
    fun provideScoreActionDao(database: LeekhaDatabase): ScoreActionDao {
        return database.scoreActionDao()
    }

    @Provides
    @Singleton
    fun provideScoreEngine(): ScoreEngine {
        return ScoreEngine()
    }

    @Provides
    @Singleton
    fun provideVersionManager(@ApplicationContext context: Context, database: LeekhaDatabase): VersionManager {
        return VersionManager(context, database)
    }
}


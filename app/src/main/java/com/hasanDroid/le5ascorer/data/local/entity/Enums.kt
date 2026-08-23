package com.hasanDroid.le5ascorer.data.local.entity

/**
 * How a match is scored.
 *
 * Team scoring was removed: the two implementations of it disagreed about who
 * was on whose team (the engine paired players 0+2 against 1+3, the repository
 * paired 0+1 against 2+3), so it never behaved consistently.
 *
 * The enum and its database column are deliberately kept rather than dropped.
 * The database is built with fallbackToDestructiveMigration(), so any schema
 * change would delete every stored match; keeping the column means no schema
 * change and no data loss. Rows that still hold "TEAM" are mapped to
 * INDIVIDUAL by Converters.
 */
enum class ScoreRule {
    INDIVIDUAL
}

enum class MatchStatus {
    IN_PROGRESS,
    COMPLETED
}

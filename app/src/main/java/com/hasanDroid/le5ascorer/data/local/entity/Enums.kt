package com.hasanDroid.le5ascorer.data.local.entity

/**
 * Which game a match belongs to, and therefore how it is scored.
 *
 * This started life as Leekha's individual-vs-team switch. Team scoring was
 * removed because the two implementations of it disagreed about who was on
 * whose team (the engine paired players 0+2 against 1+3, the repository paired
 * 0+1 against 2+3), but the enum and its column were deliberately kept: the
 * database is built with fallbackToDestructiveMigration(), so dropping the
 * column would have deleted every stored match.
 *
 * That leftover column is now the game-mode discriminator. TARNEEB is a new
 * value on the same TEXT column, so adding a second game needs no schema
 * change at all. INDIVIDUAL keeps its exact spelling so existing rows still
 * load as Leekha, and Converters maps anything unrecognised — including rows
 * that still say "TEAM" — to INDIVIDUAL rather than throwing.
 */
enum class ScoreRule {
    /** Leekha: four players scored individually. */
    INDIVIDUAL,

    /** Tarneeb: two partnerships, bid-and-trick scoring. */
    TARNEEB,

    /**
     * Trix: two partnerships, four kingdoms of five contracts. Seats 1+2 are
     * one team and 3+4 the other; terminalScore holds the setup (see TrixSetup).
     */
    TRIX
}

enum class MatchStatus {
    IN_PROGRESS,
    COMPLETED
}

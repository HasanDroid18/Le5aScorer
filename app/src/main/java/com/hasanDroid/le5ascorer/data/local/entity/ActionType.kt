package com.hasanDroid.le5ascorer.data.local.entity

/**
 * What a stored score action represents.
 *
 * The first three are Leekha's penalty cards, where `delta` is the points taken
 * and `receiverIndex` is the seat that took them.
 *
 * The TARNEEB_* values encode a Tarneeb round in the same table rather than in
 * new columns. The database is built with fallbackToDestructiveMigration() and
 * ships no schema JSON, and AppModule reacts to a migration failure by deleting
 * the database — so a schema change that could not be verified before release
 * would silently destroy stored matches. Reusing this column costs nothing:
 * it is TEXT, written through a converter, and the values are self-describing.
 *
 * A Tarneeb round stores exactly three actions:
 *  - TARNEEB_BID    receiverIndex = bidding team (0 or 1), delta = tricks bid (7..13)
 *  - TARNEEB_TRICKS receiverIndex = 0, delta = tricks team 0 actually won
 *  - TARNEEB_TRICKS receiverIndex = 1, delta = tricks team 1 actually won
 *
 * Points are never stored — TarneebScoreEngine derives them, so editing a round
 * recomputes cleanly. Leekha makes the same choice.
 */
enum class ActionType {
    HEART,
    Q_SPADES,
    TEN_DIAMONDS,
    TARNEEB_BID,
    TARNEEB_TRICKS
}

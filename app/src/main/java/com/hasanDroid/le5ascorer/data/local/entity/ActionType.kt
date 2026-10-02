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
 *
 * The TRIX_* values encode one Trix contract per round, by the same reasoning.
 * Which of them a round holds says which contract it was:
 *  - TRIX_KING      ×1  receiverIndex = team that took it, delta = doubled (0/1)
 *  - TRIX_QUEEN     ×4  receiverIndex = team, delta = suit * 2 + doubled
 *                       (suit 0 ♠, 1 ♥, 2 ♦, 3 ♣)
 *  - TRIX_DIAMONDS  ×2  receiverIndex = team, delta = diamonds taken
 *  - TRIX_LTOOSH    ×2  receiverIndex = team, delta = tricks taken
 *  - TRIX_POSITION  ×4  receiverIndex = seat, delta = finishing place 1..4
 */
enum class ActionType {
    HEART,
    Q_SPADES,
    TEN_DIAMONDS,
    TARNEEB_BID,
    TARNEEB_TRICKS,
    TRIX_KING,
    TRIX_QUEEN,
    TRIX_DIAMONDS,
    TRIX_LTOOSH,
    TRIX_POSITION
}

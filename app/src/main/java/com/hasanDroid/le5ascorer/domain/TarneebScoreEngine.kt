package com.hasanDroid.le5ascorer.domain

import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.domain.model.Round
import com.hasanDroid.le5ascorer.domain.model.ScoreAction
import javax.inject.Inject

/**
 * Tarneeb scoring.
 *
 * Two partnerships bid for tricks. One team declares how many of the thirteen
 * tricks it will take; if it delivers it scores, and if it falls short it pays.
 *
 * Deliberately separate from [ScoreEngine] rather than a mode inside it. The two
 * games disagree on the most basic thing a scorer can disagree on: in Leekha
 * reaching the target means you *lose*, in Tarneeb it means you *win*. Folding
 * both into one class would mean every method carrying a flag that inverts its
 * meaning.
 */
class TarneebScoreEngine @Inject constructor() {

    /**
     * Points for one round, indexed by team.
     *
     * @param bidderTeam which team declared the bid, 0 or 1
     * @param bid tricks declared, [MIN_BID]..[TRICKS_PER_ROUND]
     * @param tricks tricks actually won, indexed by team; must total
     *   [TRICKS_PER_ROUND]
     */
    fun scoreRound(bidderTeam: Int, bid: Int, tricks: IntArray): IntArray {
        require(bidderTeam in 0..1) { "bidderTeam must be 0 or 1, was $bidderTeam" }
        require(tricks.size == TEAMS) { "tricks must hold one entry per team" }

        val defender = 1 - bidderTeam
        val points = IntArray(TEAMS)

        if (tricks[bidderTeam] >= bid) {
            // Made it. Only the bidding team scores — the defenders get nothing
            // for the tricks they scraped together.
            points[bidderTeam] = when {
                tricks[bidderTeam] < TRICKS_PER_ROUND -> tricks[bidderTeam]
                // Kabbout: all thirteen, and they called it.
                bid == TRICKS_PER_ROUND -> KABBOUT_POINTS
                // All thirteen on a smaller bid still pays the sweep bonus.
                else -> SWEEP_POINTS
            }
        } else {
            // Fell short. The penalty is what was promised, not what was taken,
            // so an ambitious bid costs more to miss than a cautious one.
            points[bidderTeam] =
                if (bid == TRICKS_PER_ROUND) -SWEEP_POINTS else -bid
            points[defender] = sweepAware(tricks[defender])
        }

        return points
    }

    /** A defender who takes every trick is sweeping too, and is paid for it. */
    private fun sweepAware(tricks: Int): Int =
        if (tricks == TRICKS_PER_ROUND) SWEEP_POINTS else tricks

    /**
     * Running totals per team, one entry per round in play order.
     *
     * Rounds whose stored actions do not describe a bid are skipped rather than
     * scored as zero — a half-written round should not silently count.
     */
    fun calculateScoreboard(rounds: List<Round>): List<TarneebRoundResult> {
        val totals = IntArray(TEAMS)
        return rounds.sortedBy { it.roundIndex }.mapNotNull { round ->
            val entry = readRound(round.actions) ?: return@mapNotNull null
            val points = scoreRound(entry.bidderTeam, entry.bid, entry.tricks)
            totals[0] += points[0]
            totals[1] += points[1]
            TarneebRoundResult(
                roundIndex = round.roundIndex,
                bidderTeam = entry.bidderTeam,
                bid = entry.bid,
                tricks = entry.tricks.toList(),
                roundPoints = points.toList(),
                cumulative = totals.toList()
            )
        }
    }

    /**
     * Decodes the three actions a Tarneeb round stores back into a bid.
     *
     * Returns null when the bid action is missing, which is what tells
     * [calculateScoreboard] to skip the round.
     */
    fun readRound(actions: List<ScoreAction>): TarneebRoundEntry? {
        val bid = actions.firstOrNull { it.actionType == ActionType.TARNEEB_BID }
            ?: return null
        val tricks = IntArray(TEAMS)
        actions.filter { it.actionType == ActionType.TARNEEB_TRICKS }
            .forEach { if (it.receiverIndex in 0 until TEAMS) tricks[it.receiverIndex] = it.delta }
        return TarneebRoundEntry(
            bidderTeam = bid.receiverIndex.coerceIn(0, TEAMS - 1),
            bid = bid.delta,
            tricks = tricks
        )
    }

    /**
     * Whether the match is over, and who took it.
     *
     * Reaching the target *wins* here. Totals can also go negative, so a team can
     * move away from the target as well as toward it. Only one team can cross in
     * a given round — the failure branch is the only one that pays both teams,
     * and it pays the bidder negatively — but ties are resolved on total anyway
     * rather than assumed impossible.
     */
    fun checkGameWon(
        results: List<TarneebRoundResult>,
        terminalScore: Int,
        teamNames: List<String>
    ): TarneebGameResult? {
        val totals = results.lastOrNull()?.cumulative ?: return null
        if (totals.none { it >= terminalScore }) return null

        val winner = totals.indices.maxByOrNull { totals[it] } ?: return null
        val loser = 1 - winner
        return TarneebGameResult(
            winnerIndex = winner,
            winnerName = teamNames.getOrNull(winner).orEmpty(),
            loserIndex = loser,
            loserName = teamNames.getOrNull(loser).orEmpty(),
            finalScores = totals
        )
    }

    companion object {
        const val TEAMS = 2
        const val TRICKS_PER_ROUND = 13

        /**
         * Seven, not one. A bid is traditionally called 1..7 ("Lamas"), where 1
         * means seven tricks — you cannot usefully bid fewer than a majority of
         * thirteen. Tricks are what the scoring arithmetic uses, so tricks are
         * what is stored and shown.
         */
        const val MIN_BID = 7

        /** All thirteen tricks, taken without having declared all thirteen. */
        const val SWEEP_POINTS = 16

        /** All thirteen tricks, declared and delivered. */
        const val KABBOUT_POINTS = 26
    }
}

/** A round as stored: who bid, how much, and how the tricks actually fell. */
data class TarneebRoundEntry(
    val bidderTeam: Int,
    val bid: Int,
    val tricks: IntArray
) {
    // IntArray needs these written out; the generated ones compare by identity.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TarneebRoundEntry) return false
        return bidderTeam == other.bidderTeam &&
            bid == other.bid &&
            tricks.contentEquals(other.tricks)
    }

    override fun hashCode(): Int =
        (bidderTeam * 31 + bid) * 31 + tricks.contentHashCode()
}

/** One scored row of the Tarneeb scoreboard. */
data class TarneebRoundResult(
    val roundIndex: Int,
    val bidderTeam: Int,
    val bid: Int,
    val tricks: List<Int>,
    val roundPoints: List<Int>,
    val cumulative: List<Int>
) {
    /** True when the bidding team delivered what it declared. */
    val bidMade: Boolean get() = tricks[bidderTeam] >= bid
}

data class TarneebGameResult(
    val winnerIndex: Int,
    val winnerName: String,
    val loserIndex: Int,
    val loserName: String,
    val finalScores: List<Int>
)

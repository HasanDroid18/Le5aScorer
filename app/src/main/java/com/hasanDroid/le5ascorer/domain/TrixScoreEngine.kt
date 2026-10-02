package com.hasanDroid.le5ascorer.domain

import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.domain.model.Round
import com.hasanDroid.le5ascorer.domain.model.ScoreAction
import javax.inject.Inject
import kotlin.math.abs

/** The five contracts every kingdom plays once each, in whatever order its owner picks. */
enum class TrixContractType { KING, QUEENS, DIAMONDS, LTOOSH, TRIX }

/** One played contract, as entered. Teams are 0 (seats 0+1) and 1 (seats 2+3). */
sealed interface TrixContract {
    val type: TrixContractType

    data class King(val team: Int, val doubled: Boolean) : TrixContract {
        override val type get() = TrixContractType.KING
    }

    /** One take per queen, indexed by suit: 0 ♠, 1 ♥, 2 ♦, 3 ♣. */
    data class Queens(val takes: List<QueenTake>) : TrixContract {
        override val type get() = TrixContractType.QUEENS
    }

    /** Diamonds taken, indexed by team. */
    data class Diamonds(val counts: List<Int>) : TrixContract {
        override val type get() = TrixContractType.DIAMONDS
    }

    /** Tricks taken, indexed by team. */
    data class Ltoosh(val counts: List<Int>) : TrixContract {
        override val type get() = TrixContractType.LTOOSH
    }

    /** Finishing place 1..4, indexed by seat. */
    data class Trix(val places: List<Int>) : TrixContract {
        override val type get() = TrixContractType.TRIX
    }
}

data class QueenTake(val team: Int, val doubled: Boolean)

/**
 * What a Trix match fixes before the first card: who opens, and whether
 * doubling is played.
 *
 * Stored in MatchEntity.terminalScore, which Trix has no other use for. A new
 * column would mean a schema change, and this database deletes itself when a
 * migration fails — see ActionType for the same reasoning.
 */
data class TrixSetup(val openerSeat: Int, val doubling: Boolean) {
    fun encode(): Int = openerSeat + if (doubling) DOUBLING_BIT else 0

    companion object {
        private const val DOUBLING_BIT = 4

        fun decode(value: Int) = TrixSetup(
            openerSeat = value and 3,
            doubling = (value and DOUBLING_BIT) != 0
        )
    }
}

/** One scored contract, in play order. */
data class TrixContractResult(
    val roundId: Long,
    val roundIndex: Int,
    val contract: TrixContract,
    val kingdom: Int,
    val ownerSeat: Int,
    val points: List<Int>,
    val totals: List<Int>
)

data class TrixScoreboard(
    val results: List<TrixContractResult>,
    val totals: List<Int>,
    /** 0..3 while playing; [TrixScoreEngine.KINGDOMS] once all twenty are in. */
    val kingdom: Int,
    val ownerSeat: Int,
    val playedInKingdom: Set<TrixContractType>,
    val isGameOver: Boolean,
    /** 0 or 1 once the game is over; null for a draw or a game still running. */
    val winnerTeam: Int?
)

/**
 * Partnership Trix scoring.
 *
 * Separate from ScoreEngine and TarneebScoreEngine for the same reason those two
 * are separate from each other: the games share a table, not rules. Nothing is
 * stored as points — every total is derived here from what was entered, so
 * editing a contract recomputes the whole game.
 */
class TrixScoreEngine @Inject constructor() {

    /** Points for one contract, indexed by team. */
    fun scoreContract(contract: TrixContract, doubling: Boolean): List<Int> {
        val points = IntArray(TEAMS)
        when (contract) {
            is TrixContract.King ->
                penalise(points, contract.team, KING_POINTS, doubling && contract.doubled)
            is TrixContract.Queens -> contract.takes.forEach {
                penalise(points, it.team, QUEEN_POINTS, doubling && it.doubled)
            }
            is TrixContract.Diamonds ->
                contract.counts.forEachIndexed { team, n -> points[team] -= n * DIAMOND_POINTS }
            is TrixContract.Ltoosh ->
                contract.counts.forEachIndexed { team, n -> points[team] -= n * LTOOSH_POINTS }
            is TrixContract.Trix -> contract.places.forEachIndexed { seat, place ->
                points[teamOf(seat)] += TRIX_POINTS[place - 1]
            }
        }
        return points.toList()
    }

    /** Doubled: the taker pays twice over and the other team collects the single value. */
    private fun penalise(points: IntArray, team: Int, value: Int, doubled: Boolean) {
        if (doubled) {
            points[team] -= value * 2
            points[1 - team] += value
        } else {
            points[team] -= value
        }
    }

    fun ownerSeat(setup: TrixSetup, kingdom: Int): Int =
        ROTATION[(ROTATION.indexOf(setup.openerSeat) + kingdom) % ROTATION.size]

    /**
     * The whole game so far.
     *
     * Kingdoms are counted by position among readable rounds rather than by
     * roundIndex, so a round that cannot be read is skipped without leaving a
     * hole that would shift every later contract into the wrong kingdom.
     */
    fun calculateScoreboard(rounds: List<Round>, setup: TrixSetup): TrixScoreboard {
        val totals = IntArray(TEAMS)
        val results = rounds.sortedBy { it.roundIndex }
            .mapNotNull { round -> readRound(round.actions)?.let { round to it } }
            .mapIndexed { position, (round, contract) ->
                val points = scoreContract(contract, setup.doubling)
                totals[0] += points[0]
                totals[1] += points[1]
                val kingdom = position / CONTRACTS_PER_KINGDOM
                TrixContractResult(
                    roundId = round.id,
                    roundIndex = round.roundIndex,
                    contract = contract,
                    kingdom = kingdom,
                    ownerSeat = ownerSeat(setup, kingdom),
                    points = points,
                    totals = totals.toList()
                )
            }

        val played = results.map { it.contract.type }
        val kingdom = played.size / CONTRACTS_PER_KINGDOM
        val over = isGameOver(totals.toList(), played, setup.doubling)
        return TrixScoreboard(
            results = results,
            totals = totals.toList(),
            kingdom = kingdom,
            ownerSeat = ownerSeat(setup, kingdom.coerceAtMost(KINGDOMS - 1)),
            playedInKingdom = played.drop(kingdom * CONTRACTS_PER_KINGDOM).toSet(),
            isGameOver = over,
            winnerTeam = when {
                !over || totals[0] == totals[1] -> null
                totals[0] > totals[1] -> 0
                else -> 1
            }
        )
    }

    /**
     * Twenty contracts, or a gap the trailing team can no longer close.
     *
     * The early check starts with kingdom 3. A gap exactly equal to what is
     * left to play still allows a tie, so only a strictly larger gap ends it.
     */
    fun isGameOver(totals: List<Int>, played: List<TrixContractType>, doubling: Boolean): Boolean {
        if (played.size >= TOTAL_CONTRACTS) return true
        if (played.size <= EARLY_CHECK_AFTER) return false
        return abs(totals[0] - totals[1]) > remainingSwing(played, doubling)
    }

    /** The most the gap can still move: every unplayed contract at its maximum swing. */
    fun remainingSwing(played: List<TrixContractType>, doubling: Boolean): Int {
        if (played.size >= TOTAL_CONTRACTS) return 0
        val kingdom = played.size / CONTRACTS_PER_KINGDOM
        val playedNow = played.drop(kingdom * CONTRACTS_PER_KINGDOM).toSet()
        val fullKingdom = TrixContractType.entries.sumOf { maxSwing(it, doubling) }
        val current = TrixContractType.entries
            .filter { it !in playedNow }
            .sumOf { maxSwing(it, doubling) }
        return current + (KINGDOMS - kingdom - 1) * fullKingdom
    }

    /** How far one contract can move the gap toward the trailing team. */
    fun maxSwing(type: TrixContractType, doubling: Boolean): Int = when (type) {
        // Doubled: the leader pays 150 and the trailer collects 75.
        TrixContractType.KING -> if (doubling) KING_POINTS * 3 else KING_POINTS
        TrixContractType.QUEENS ->
            QUEENS_PER_DECK * if (doubling) QUEEN_POINTS * 3 else QUEEN_POINTS
        TrixContractType.DIAMONDS -> HAND_SIZE * DIAMOND_POINTS
        TrixContractType.LTOOSH -> HAND_SIZE * LTOOSH_POINTS
        // Best case for one team is 1st + 2nd against 3rd + 4th.
        TrixContractType.TRIX -> TRIX_POINTS[0] + TRIX_POINTS[1] - TRIX_POINTS[2] - TRIX_POINTS[3]
    }

    /** How a contract is stored; the inverse of [readRound]. See ActionType. */
    fun toActions(contract: TrixContract): List<ScoreAction> = when (contract) {
        is TrixContract.King ->
            listOf(action(ActionType.TRIX_KING, contract.team, if (contract.doubled) 1 else 0))
        is TrixContract.Queens -> contract.takes.mapIndexed { suit, take ->
            action(ActionType.TRIX_QUEEN, take.team, suit * 2 + if (take.doubled) 1 else 0)
        }
        is TrixContract.Diamonds ->
            contract.counts.mapIndexed { team, n -> action(ActionType.TRIX_DIAMONDS, team, n) }
        is TrixContract.Ltoosh ->
            contract.counts.mapIndexed { team, n -> action(ActionType.TRIX_LTOOSH, team, n) }
        is TrixContract.Trix ->
            contract.places.mapIndexed { seat, place -> action(ActionType.TRIX_POSITION, seat, place) }
    }

    private fun action(type: ActionType, receiver: Int, delta: Int) =
        ScoreAction(roundId = 0, receiverIndex = receiver, actionType = type, delta = delta)

    /**
     * Decodes a stored round, or null when it does not describe exactly one
     * complete contract — which tells [calculateScoreboard] to skip it.
     */
    fun readRound(actions: List<ScoreAction>): TrixContract? {
        val type = actions.map { it.actionType }.distinct().singleOrNull() ?: return null
        return when (type) {
            ActionType.TRIX_KING -> actions.singleOrNull()
                ?.takeIf { it.receiverIndex in TEAM_RANGE && it.delta in 0..1 }
                ?.let { TrixContract.King(it.receiverIndex, it.delta == 1) }
            ActionType.TRIX_QUEEN -> readQueens(actions)
            ActionType.TRIX_DIAMONDS -> readCounts(actions)?.let { TrixContract.Diamonds(it) }
            ActionType.TRIX_LTOOSH -> readCounts(actions)?.let { TrixContract.Ltoosh(it) }
            ActionType.TRIX_POSITION -> readPlaces(actions)
            else -> null
        }
    }

    private fun readQueens(actions: List<ScoreAction>): TrixContract? {
        if (actions.any { it.receiverIndex !in TEAM_RANGE || it.delta < 0 }) return null
        val bySuit = actions.associateBy { it.delta / 2 }
        if (actions.size != QUEENS_PER_DECK || bySuit.keys != (0 until QUEENS_PER_DECK).toSet()) {
            return null
        }
        return TrixContract.Queens(
            (0 until QUEENS_PER_DECK).map { suit ->
                val take = bySuit.getValue(suit)
                QueenTake(take.receiverIndex, take.delta % 2 == 1)
            }
        )
    }

    /** Two team counts that between them account for all thirteen. */
    private fun readCounts(actions: List<ScoreAction>): List<Int>? {
        val byTeam = actions.associate { it.receiverIndex to it.delta }
        if (actions.size != TEAMS || byTeam.keys != TEAM_RANGE.toSet()) return null
        val counts = TEAM_RANGE.map { byTeam.getValue(it) }
        return counts.takeIf { c -> c.all { it >= 0 } && c.sum() == HAND_SIZE }
    }

    /** Every seat placed, and every place used once. */
    private fun readPlaces(actions: List<ScoreAction>): TrixContract? {
        val bySeat = actions.associate { it.receiverIndex to it.delta }
        if (actions.size != SEATS || bySeat.keys != (0 until SEATS).toSet()) return null
        if (bySeat.values.toSet() != (1..SEATS).toSet()) return null
        return TrixContract.Trix((0 until SEATS).map { bySeat.getValue(it) })
    }

    companion object {
        const val TEAMS = 2
        const val SEATS = 4
        const val KINGDOMS = 4
        const val CONTRACTS_PER_KINGDOM = 5
        const val TOTAL_CONTRACTS = KINGDOMS * CONTRACTS_PER_KINGDOM

        /** The early game-over check starts once two full kingdoms are in. */
        const val EARLY_CHECK_AFTER = 2 * CONTRACTS_PER_KINGDOM

        /** Thirteen diamonds, and thirteen tricks. */
        const val HAND_SIZE = 13
        const val QUEENS_PER_DECK = 4

        const val KING_POINTS = 75
        const val QUEEN_POINTS = 25
        const val DIAMOND_POINTS = 10
        const val LTOOSH_POINTS = 15

        /** Paid for finishing 1st, 2nd, 3rd and 4th. */
        val TRIX_POINTS = listOf(200, 150, 100, 50)

        /** Seats in ownership order: P1 → P3 → P2 → P4, partners alternating. */
        private val ROTATION = listOf(0, 2, 1, 3)
        private val TEAM_RANGE = 0 until TEAMS

        fun teamOf(seat: Int): Int = seat / 2
    }
}

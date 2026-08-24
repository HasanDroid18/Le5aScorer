package com.hasanDroid.le5ascorer.ui.tarneeb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.TarneebScoreEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TarneebRoundUiState(
    val matchId: Long = 0,
    val roundId: Long? = null,
    val teamNames: List<String> = emptyList(),
    val bidderTeam: Int = 0,
    val bid: Int = TarneebScoreEngine.MIN_BID,
    /** Tricks won, indexed by team. Always sums to 13 or less while editing. */
    val tricks: List<Int> = listOf(0, 0),
    /** True while an existing round is being read back for editing. */
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false
) {
    val assigned: Int get() = tricks.sum()
    val remaining: Int get() = TarneebScoreEngine.TRICKS_PER_ROUND - assigned

    /** Every one of the thirteen tricks has to belong to someone. */
    val isComplete: Boolean get() = assigned == TarneebScoreEngine.TRICKS_PER_ROUND
}

@HiltViewModel
class TarneebRoundEntryViewModel @Inject constructor(
    private val repository: LeekhaRepository,
    private val engine: TarneebScoreEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TarneebRoundUiState())
    val uiState: StateFlow<TarneebRoundUiState> = _uiState

    private var initialized = false

    fun initialize(matchId: Long, teamNames: List<String>, roundId: Long?) {
        if (initialized) return
        initialized = true

        _uiState.value = TarneebRoundUiState(
            matchId = matchId,
            roundId = roundId,
            teamNames = teamNames,
            // Editing reads the stored round asynchronously, and that read
            // overwrites exactly the three fields the user can change. Without
            // this the controls are live during the read and any tap made in
            // that window is silently discarded.
            isLoading = roundId != null
        )

        // Editing an existing round: load what was stored so the screen opens on
        // the round as it stands rather than blank.
        if (roundId != null) {
            viewModelScope.launch {
                val round = repository.getRoundWithActions(roundId)
                val entry = round?.actions?.let(engine::readRound)
                if (entry == null) {
                    // Nothing readable stored. Fall back to a blank round rather
                    // than leaving the screen permanently inert — Save will
                    // still replace the existing round, which is the recovery
                    // path if its actions were ever lost.
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    bidderTeam = entry.bidderTeam,
                    bid = entry.bid,
                    tricks = entry.tricks.toList(),
                    isLoading = false
                )
            }
        }
    }

    fun setBidder(team: Int) {
        _uiState.value = _uiState.value.copy(bidderTeam = team)
    }

    fun changeBid(delta: Int) {
        val next = (_uiState.value.bid + delta)
            .coerceIn(TarneebScoreEngine.MIN_BID, TarneebScoreEngine.TRICKS_PER_ROUND)
        _uiState.value = _uiState.value.copy(bid = next)
    }

    /**
     * Moves one trick to or from a team.
     *
     * Adding takes from the unassigned pool while there is one, and once every
     * trick is spoken for it takes one **from the other team** instead. That
     * second half is what makes an existing round editable: editing always opens
     * on a complete round, so a rule that only ever drew from an empty pool left
     * both "+" buttons dead and the split unchangeable.
     *
     * The thirteen tricks stay fully accounted for either way — the screen can
     * never reach a total that is not 13 or less — so validation stays a
     * property of the control rather than a check after the fact.
     */
    fun changeTricks(team: Int, delta: Int) {
        val state = _uiState.value
        if (team !in state.tricks.indices || delta == 0) return

        val other = 1 - team
        val current = state.tricks[team]
        val otherCurrent = state.tricks.getOrElse(other) { 0 }
        val updated = state.tricks.toMutableList()

        if (delta > 0) {
            val fromPool = minOf(delta, state.remaining)
            val fromOther = minOf(delta - fromPool, otherCurrent)
            if (fromPool + fromOther == 0) return
            updated[team] = current + fromPool + fromOther
            if (fromOther > 0) updated[other] = otherCurrent - fromOther
        } else {
            // Removing simply returns tricks to the pool; it never touches the
            // other team, so a round can always be taken back apart.
            val next = (current + delta).coerceAtLeast(0)
            if (next == current) return
            updated[team] = next
        }

        _uiState.value = state.copy(tricks = updated)
    }

    /**
     * Whether this team's "+" can do anything: either the pool has a trick left,
     * or the other team has one to hand over.
     */
    fun canAddTrick(team: Int): Boolean {
        val state = _uiState.value
        return state.remaining > 0 || state.tricks.getOrElse(1 - team) { 0 } > 0
    }

    /** Gives every unassigned trick to one team — the usual way to finish. */
    fun assignRemainingTo(team: Int) {
        val state = _uiState.value
        if (state.remaining <= 0) return
        changeTricks(team, state.remaining)
    }

    /** What this round would award, shown live so the entry can be sanity-checked. */
    fun previewPoints(): List<Int> {
        val state = _uiState.value
        if (!state.isComplete) return listOf(0, 0)
        return engine.scoreRound(state.bidderTeam, state.bid, state.tricks.toIntArray()).toList()
    }

    fun save() {
        val state = _uiState.value
        if (!state.isComplete || state.isSaving) return

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            if (state.roundId == null) {
                repository.addTarneebRound(state.matchId, state.bidderTeam, state.bid, state.tricks)
            } else {
                repository.updateTarneebRound(state.roundId, state.bidderTeam, state.bid, state.tricks)
            }
            _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
        }
    }

    fun reset() {
        _uiState.value = _uiState.value.copy(
            bidderTeam = 0,
            bid = TarneebScoreEngine.MIN_BID,
            tricks = listOf(0, 0)
        )
    }
}

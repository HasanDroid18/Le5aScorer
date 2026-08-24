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
            teamNames = teamNames
        )

        // Editing an existing round: load what was stored so the screen opens on
        // the round as it stands rather than blank.
        if (roundId != null) {
            viewModelScope.launch {
                val round = repository.getRoundWithActions(roundId) ?: return@launch
                val entry = engine.readRound(round.actions) ?: return@launch
                _uiState.value = _uiState.value.copy(
                    bidderTeam = entry.bidderTeam,
                    bid = entry.bid,
                    tricks = entry.tricks.toList()
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
     * The thirteen tricks are a fixed pool, so a team can never hold more than
     * are left unassigned — that is enforced here rather than validated after
     * the fact, which means the screen can never reach an impossible state.
     */
    fun changeTricks(team: Int, delta: Int) {
        val state = _uiState.value
        val current = state.tricks.getOrElse(team) { 0 }
        val otherTotal = state.assigned - current
        val next = (current + delta)
            .coerceIn(0, TarneebScoreEngine.TRICKS_PER_ROUND - otherTotal)
        if (next == current) return

        _uiState.value = state.copy(
            tricks = state.tricks.mapIndexed { index, value -> if (index == team) next else value }
        )
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

package com.hasanDroid.le5ascorer.ui.trix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.QueenTake
import com.hasanDroid.le5ascorer.domain.TrixContract
import com.hasanDroid.le5ascorer.domain.TrixContractType
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine.Companion.HAND_SIZE
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine.Companion.QUEENS_PER_DECK
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine.Companion.SEATS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The answers for one contract. Only the fields for [type] matter; the rest stay
 * at their defaults. Unanswered questions are null rather than a guessed default,
 * so Confirm cannot save a contract nobody actually entered.
 */
data class TrixRoundUiState(
    val matchId: Long = 0,
    val roundId: Long? = null,
    val type: TrixContractType = TrixContractType.KING,
    val playerNames: List<String> = emptyList(),
    val doubling: Boolean = false,
    val kingTeam: Int? = null,
    val kingDoubled: Boolean = false,
    val queenTeams: List<Int?> = List(QUEENS_PER_DECK) { null },
    val queenDoubled: List<Boolean> = List(QUEENS_PER_DECK) { false },
    val counts: List<Int> = listOf(0, 0),
    val places: List<Int?> = List(SEATS) { null },
    /** True while an existing contract is being read back for editing. */
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false
) {
    val teamNames: List<String> get() = trixTeamNames(playerNames)

    /** The entry as a contract, or null while anything is still unanswered. */
    fun toContract(): TrixContract? = when (type) {
        TrixContractType.KING -> kingTeam?.let { TrixContract.King(it, doubling && kingDoubled) }
        TrixContractType.QUEENS -> queenTeams.filterNotNull()
            .takeIf { it.size == QUEENS_PER_DECK }
            ?.let { teams ->
                TrixContract.Queens(
                    teams.mapIndexed { suit, team -> QueenTake(team, doubling && queenDoubled[suit]) }
                )
            }
        TrixContractType.DIAMONDS -> counts.takeIf { it.sum() == HAND_SIZE }?.let { TrixContract.Diamonds(it) }
        TrixContractType.LTOOSH -> counts.takeIf { it.sum() == HAND_SIZE }?.let { TrixContract.Ltoosh(it) }
        TrixContractType.TRIX -> places.filterNotNull()
            .takeIf { it.size == SEATS }
            ?.let { TrixContract.Trix(it) }
    }

    /**
     * One more or one fewer for a team. Adding draws from the unassigned pool
     * while there is one and from the other team once all thirteen are placed —
     * the same rule as Tarneeb's tricks, which is what makes a complete split
     * editable. Removing only ever returns cards to the pool.
     */
    fun withCountChange(team: Int, delta: Int): TrixRoundUiState {
        val other = 1 - team
        val updated = counts.toMutableList()
        if (delta > 0) {
            val fromPool = minOf(delta, HAND_SIZE - counts.sum())
            val fromOther = minOf(delta - fromPool, counts[other])
            updated[team] += fromPool + fromOther
            updated[other] -= fromOther
        } else {
            updated[team] = (counts[team] + delta).coerceAtLeast(0)
        }
        return copy(counts = updated)
    }

    /** Gives a seat a place; whoever held that place loses it. */
    fun withPlace(seat: Int, place: Int): TrixRoundUiState = copy(
        places = places.mapIndexed { i, held ->
            when {
                i == seat -> place
                held == place -> null
                else -> held
            }
        }
    )

    /** Loads a stored contract back into the answers, for editing. */
    fun withContract(contract: TrixContract): TrixRoundUiState = when (contract) {
        is TrixContract.King -> copy(kingTeam = contract.team, kingDoubled = contract.doubled)
        is TrixContract.Queens -> copy(
            queenTeams = contract.takes.map { it.team },
            queenDoubled = contract.takes.map { it.doubled }
        )
        is TrixContract.Diamonds -> copy(counts = contract.counts)
        is TrixContract.Ltoosh -> copy(counts = contract.counts)
        is TrixContract.Trix -> copy(places = contract.places)
    }
}

@HiltViewModel
class TrixRoundEntryViewModel @Inject constructor(
    private val repository: LeekhaRepository,
    private val engine: TrixScoreEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrixRoundUiState())
    val uiState: StateFlow<TrixRoundUiState> = _uiState

    private var initialized = false

    fun initialize(
        matchId: Long,
        type: TrixContractType,
        playerNames: List<String>,
        doubling: Boolean,
        roundId: Long?
    ) {
        if (initialized) return
        initialized = true

        _uiState.value = TrixRoundUiState(
            matchId = matchId,
            roundId = roundId,
            type = type,
            playerNames = playerNames,
            doubling = doubling,
            // Controls stay inert while the stored contract is read back, so a
            // tap in that window is not silently overwritten.
            isLoading = roundId != null
        )
        if (roundId == null) return

        viewModelScope.launch {
            val contract = repository.getRoundWithActions(roundId)?.actions?.let(engine::readRound)
            // Unreadable: open blank rather than inert. Confirm still replaces
            // the stored round, which is the recovery path.
            _uiState.update { state ->
                (contract?.let(state::withContract) ?: state).copy(isLoading = false)
            }
        }
    }

    fun setKingTeam(team: Int) = _uiState.update { it.copy(kingTeam = team) }

    fun setKingDoubled(doubled: Boolean) = _uiState.update { it.copy(kingDoubled = doubled) }

    fun setQueenTeam(suit: Int, team: Int) = _uiState.update {
        it.copy(queenTeams = it.queenTeams.toMutableList().apply { set(suit, team) })
    }

    fun setQueenDoubled(suit: Int, doubled: Boolean) = _uiState.update {
        it.copy(queenDoubled = it.queenDoubled.toMutableList().apply { set(suit, doubled) })
    }

    fun changeCount(team: Int, delta: Int) = _uiState.update { it.withCountChange(team, delta) }

    fun setPlace(seat: Int, place: Int) = _uiState.update { it.withPlace(seat, place) }

    /** What the contract will pay, shown before it is confirmed. */
    fun previewPoints(): List<Int>? {
        val state = _uiState.value
        return state.toContract()?.let { engine.scoreContract(it, state.doubling) }
    }

    fun save() {
        val state = _uiState.value
        val contract = state.toContract() ?: return
        if (state.isSaving || state.isLoading) return

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            if (state.roundId == null) {
                repository.addTrixRound(state.matchId, contract)
            } else {
                repository.updateTrixRound(state.roundId, contract)
            }
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }
}

package com.hasanDroid.le5ascorer.ui.trix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.local.entity.MatchStatus
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.TrixContractType
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine
import com.hasanDroid.le5ascorer.domain.TrixScoreboard
import com.hasanDroid.le5ascorer.domain.TrixSetup
import com.hasanDroid.le5ascorer.domain.model.MatchDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrixScoreboardUiState(
    val matchDetail: MatchDetail? = null,
    val setup: TrixSetup = TrixSetup(openerSeat = 0, doubling = false),
    val board: TrixScoreboard? = null,
    /** The contract the owner has picked to play next. */
    val selected: TrixContractType? = null
) {
    val playerNames: List<String>
        get() = matchDetail?.match?.players?.map { it.name } ?: emptyList()

    val teamNames: List<String> get() = trixTeamNames(playerNames)
}

@HiltViewModel
class TrixScoreboardViewModel @Inject constructor(
    private val repository: LeekhaRepository,
    private val engine: TrixScoreEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrixScoreboardUiState())
    val uiState: StateFlow<TrixScoreboardUiState> = _uiState

    private var loaded = false

    fun load(matchId: Long) {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            repository.observeMatchDetail(matchId).collect { detail ->
                if (detail == null) return@collect

                val setup = TrixSetup.decode(detail.match.terminalScore)
                val board = engine.calculateScoreboard(detail.rounds, setup)
                _uiState.update { state ->
                    // A selection that has since been played (or a finished
                    // game) must not leave Enter Round pointing at it.
                    val stillOpen = state.selected?.takeIf {
                        !board.isGameOver && it !in board.playedInKingdom
                    }
                    state.copy(matchDetail = detail, setup = setup, board = board, selected = stillOpen)
                }

                // Editing a contract can end a game or un-end it, so this reopens
                // as well as completes — the same as Tarneeb.
                syncStatus(detail, board.isGameOver)
            }
        }
    }

    fun select(type: TrixContractType) {
        val board = _uiState.value.board ?: return
        if (board.isGameOver || type in board.playedInKingdom) return
        _uiState.update { it.copy(selected = type) }
    }

    /** Keeps the captured photo, which would otherwise be taken and dropped. */
    fun saveLoserImage(path: String) {
        val matchId = _uiState.value.matchDetail?.match?.id ?: return
        viewModelScope.launch { repository.saveLoserImage(matchId, path) }
    }

    private suspend fun syncStatus(detail: MatchDetail, isOver: Boolean) {
        val wasOver = detail.match.status == MatchStatus.COMPLETED
        when {
            isOver && !wasOver -> repository.completeMatch(detail.match.id)
            !isOver && wasOver -> repository.reopenMatch(detail.match.id)
        }
    }
}

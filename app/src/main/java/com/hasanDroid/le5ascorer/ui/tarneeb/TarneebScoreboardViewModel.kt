package com.hasanDroid.le5ascorer.ui.tarneeb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.local.entity.MatchStatus
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.TarneebGameResult
import com.hasanDroid.le5ascorer.domain.TarneebRoundResult
import com.hasanDroid.le5ascorer.domain.TarneebScoreEngine
import com.hasanDroid.le5ascorer.domain.model.MatchDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TarneebScoreboardUiState(
    val matchDetail: MatchDetail? = null,
    val results: List<TarneebRoundResult> = emptyList(),
    val isLoading: Boolean = true,
    val gameResult: TarneebGameResult? = null
) {
    val teamNames: List<String>
        get() = matchDetail?.match?.players?.map { it.name } ?: emptyList()

    val totals: List<Int>
        get() = results.lastOrNull()?.cumulative ?: List(TarneebScoreEngine.TEAMS) { 0 }
}

@HiltViewModel
class TarneebScoreboardViewModel @Inject constructor(
    private val repository: LeekhaRepository,
    private val engine: TarneebScoreEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TarneebScoreboardUiState())
    val uiState: StateFlow<TarneebScoreboardUiState> = _uiState

    private var loaded = false

    fun load(matchId: Long) {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            repository.observeMatchDetail(matchId).collect { detail ->
                if (detail == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@collect
                }

                val results = engine.calculateScoreboard(detail.rounds)
                val teamNames = detail.match.players.map { it.name }
                val gameResult =
                    engine.checkGameWon(results, detail.match.terminalScore, teamNames)

                _uiState.value = TarneebScoreboardUiState(
                    matchDetail = detail,
                    results = results,
                    isLoading = false,
                    gameResult = gameResult
                )

                // Keep the stored status in step with the scores. Editing a round
                // can un-win a match as easily as win it, so this reopens as well
                // as completes rather than only ever closing.
                syncStatus(detail, gameResult != null)
            }
        }
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

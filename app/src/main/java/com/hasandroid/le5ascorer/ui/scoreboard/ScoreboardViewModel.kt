package com.hasandroid.le5ascorer.ui.scoreboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasandroid.le5ascorer.domain.ScoreEngine
import com.hasandroid.le5ascorer.domain.GameOverResult
import com.hasandroid.le5ascorer.domain.model.MatchDetail
import com.hasandroid.le5ascorer.domain.usecase.CompleteMatchUseCase
import com.hasandroid.le5ascorer.domain.usecase.GetMatchDetailUseCase
import com.hasandroid.le5ascorer.domain.usecase.ReopenMatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScoreboardUiState(
    val matchDetail: MatchDetail? = null,
    val isLoading: Boolean = true,
    val gameOver: GameOverResult? = null
)

@HiltViewModel
class ScoreboardViewModel @Inject constructor(
    private val getMatchDetailUseCase: GetMatchDetailUseCase,
    private val completeMatchUseCase: CompleteMatchUseCase,
    private val reopenMatchUseCase: ReopenMatchUseCase,
    private val scoreEngine: ScoreEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScoreboardUiState())
    val uiState: StateFlow<ScoreboardUiState> = _uiState.asStateFlow()

    fun loadMatch(matchId: Long) {
        viewModelScope.launch {
            getMatchDetailUseCase(matchId).collect { detail ->
                val gameOver = detail?.let {
                    scoreEngine.checkGameOver(
                        it.scoreboard,
                        it.match.terminalScore,
                        it.match.scoreRule
                    )
                }

                // Auto-complete match if game is over
                if (gameOver != null && detail?.match?.status == com.hasandroid.le5ascorer.data.local.entity.MatchStatus.IN_PROGRESS) {
                    completeMatchUseCase(matchId)
                }
                // Set match back to IN_PROGRESS if game is no longer over (after editing)
                else if (gameOver == null && detail?.match?.status == com.hasandroid.le5ascorer.data.local.entity.MatchStatus.COMPLETED) {
                    reopenMatchUseCase(matchId)
                }

                _uiState.value = ScoreboardUiState(
                    matchDetail = detail,
                    isLoading = false,
                    gameOver = gameOver
                )
            }
        }
    }
}


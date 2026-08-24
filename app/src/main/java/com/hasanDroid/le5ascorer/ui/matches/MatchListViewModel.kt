package com.hasanDroid.le5ascorer.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.domain.model.Match
import com.hasanDroid.le5ascorer.domain.usecase.MatchUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchListUiState(
    val inProgressMatches: List<Match> = emptyList(),
    val completedMatches: List<Match> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MatchListViewModel @Inject constructor(
    private val matchUseCases: MatchUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchListUiState())
    val uiState: StateFlow<MatchListUiState> = _uiState

    /**
     * Which game's matches this list shows. Set once by the fragment from its
     * navigation argument, before collection starts — the same list screen
     * serves Leekha and Tarneeb, and they must never show each other's matches.
     */
    private var scoreRule: ScoreRule = ScoreRule.INDIVIDUAL
    private var started = false

    fun start(scoreRule: ScoreRule) {
        if (started) return
        started = true
        this.scoreRule = scoreRule
        loadMatches()
    }

    private fun loadMatches() {
        viewModelScope.launch {
            combine(
                matchUseCases.getInProgressMatches(scoreRule),
                matchUseCases.getCompletedMatches(scoreRule)
            ) { inProgress, completed ->
                MatchListUiState(
                    inProgressMatches = inProgress,
                    completedMatches = completed,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun deleteMatch(matchId: Long) {
        viewModelScope.launch {
            matchUseCases.deleteMatch(matchId)
        }
    }

    fun duplicateMatch(matchId: Long) {
        viewModelScope.launch {
            matchUseCases.duplicateMatch(matchId)
        }
    }
}


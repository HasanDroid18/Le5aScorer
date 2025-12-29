package com.hasanDroid.le5ascorer.ui.newmatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.domain.usecase.CreateMatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewMatchUiState(
    val player1Name: String = "",
    val player2Name: String = "",
    val player3Name: String = "",
    val player4Name: String = "",
    val terminalScore: Int = 101,
    val scoreRule: ScoreRule = ScoreRule.INDIVIDUAL,
    val isValid: Boolean = false,
    val isCreating: Boolean = false,
    val createdMatchId: Long? = null
)

@HiltViewModel
class NewMatchViewModel @Inject constructor(
    private val createMatchUseCase: CreateMatchUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewMatchUiState())
    val uiState: StateFlow<NewMatchUiState> = _uiState.asStateFlow()

    fun updatePlayer1Name(name: String) {
        _uiState.value = _uiState.value.copy(player1Name = name)
        validateForm()
    }

    fun updatePlayer2Name(name: String) {
        _uiState.value = _uiState.value.copy(player2Name = name)
        validateForm()
    }

    fun updatePlayer3Name(name: String) {
        _uiState.value = _uiState.value.copy(player3Name = name)
        validateForm()
    }

    fun updatePlayer4Name(name: String) {
        _uiState.value = _uiState.value.copy(player4Name = name)
        validateForm()
    }

    fun updateTerminalScore(score: Int) {
        _uiState.value = _uiState.value.copy(terminalScore = score)
    }

    fun updateScoreRule(rule: ScoreRule) {
        _uiState.value = _uiState.value.copy(scoreRule = rule)
    }

    private fun validateForm() {
        val state = _uiState.value
        val isValid = state.player1Name.isNotBlank() &&
                state.player2Name.isNotBlank() &&
                state.player3Name.isNotBlank() &&
                state.player4Name.isNotBlank()
        _uiState.value = state.copy(isValid = isValid)
    }

    fun createMatch() {
        val state = _uiState.value
        if (!state.isValid || state.isCreating) return

        _uiState.value = state.copy(isCreating = true)

        viewModelScope.launch {
            try {
                val matchId = createMatchUseCase(
                    player1Name = state.player1Name.trim(),
                    player2Name = state.player2Name.trim(),
                    player3Name = state.player3Name.trim(),
                    player4Name = state.player4Name.trim(),
                    terminalScore = state.terminalScore,
                    scoreRule = state.scoreRule
                )
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdMatchId = matchId
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false)
            }
        }
    }

    fun resetCreatedMatchId() {
        _uiState.value = _uiState.value.copy(createdMatchId = null)
    }
}


package com.hasanDroid.le5ascorer.ui.newmatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.usecase.CreateMatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewMatchUiState(
    val player1Name: String = "",
    val player2Name: String = "",
    val player3Name: String = "",
    val player4Name: String = "",
    val terminalScore: Int = 101,
    val isValid: Boolean = false,
    val isCreating: Boolean = false,
    val createdMatchId: Long? = null
) {
    val names: List<String> get() = listOf(player1Name, player2Name, player3Name, player4Name)
}

@HiltViewModel
class NewMatchViewModel @Inject constructor(
    private val createMatchUseCase: CreateMatchUseCase,
    repository: LeekhaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewMatchUiState())
    val uiState: StateFlow<NewMatchUiState> = _uiState.asStateFlow()

    /**
     * Everyone who has played before, most recent first.
     *
     * `PlayerDao.getAllPlayers` already orders by `lastUsedAt DESC`, and
     * `getOrCreatePlayer` bumps that timestamp every time a match is created,
     * so recency is maintained without any extra bookkeeping here.
     */
    val knownPlayers: StateFlow<List<String>> = repository.getAllPlayers()
        .map { players -> players.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updatePlayer1Name(name: String) = updateName { it.copy(player1Name = name) }
    fun updatePlayer2Name(name: String) = updateName { it.copy(player2Name = name) }
    fun updatePlayer3Name(name: String) = updateName { it.copy(player3Name = name) }
    fun updatePlayer4Name(name: String) = updateName { it.copy(player4Name = name) }

    private fun updateName(transform: (NewMatchUiState) -> NewMatchUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next.copy(isValid = isComplete(next))
    }

    fun updateTerminalScore(score: Int) {
        _uiState.value = _uiState.value.copy(terminalScore = score)
    }

    /**
     * Index of the first empty seat, or null when the line-up is full. Used by
     * the quick-pick chips so tapping a name fills the next gap.
     */
    fun firstEmptySeat(): Int? =
        _uiState.value.names.indexOfFirst { it.isBlank() }.takeIf { it >= 0 }

    /**
     * Suggestions for one field: known players, minus anyone already seated
     * elsewhere, so the same person cannot be picked into two seats.
     */
    fun suggestionsFor(seat: Int): List<String> {
        val taken = _uiState.value.names
            .filterIndexed { index, _ -> index != seat }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return knownPlayers.value.filter { it.trim().lowercase() !in taken }
    }

    /** A name is only a duplicate if it collides with a *different* seat. */
    private fun isComplete(state: NewMatchUiState): Boolean {
        val trimmed = state.names.map { it.trim() }
        if (trimmed.any { it.isBlank() }) return false
        return trimmed.map { it.lowercase() }.toSet().size == trimmed.size
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
                    terminalScore = state.terminalScore
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

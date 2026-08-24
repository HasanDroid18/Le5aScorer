package com.hasanDroid.le5ascorer.ui.tarneeb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewTarneebMatchUiState(
    val teamAName: String = "",
    val teamBName: String = "",
    val terminalScore: Int = DEFAULT_TARGET,
    val isCreating: Boolean = false,
    val createdMatchId: Long? = null
) {
    val names: List<String> get() = listOf(teamAName, teamBName)

    /** Both named, and not the same name twice. */
    val isComplete: Boolean
        get() = names.all { it.isNotBlank() } &&
            !teamAName.trim().equals(teamBName.trim(), ignoreCase = true)

    /** Distinguishes "not filled in yet" from "filled in wrong". */
    val hasDuplicateNames: Boolean
        get() = names.all { it.isNotBlank() } &&
            teamAName.trim().equals(teamBName.trim(), ignoreCase = true)

    companion object {
        const val DEFAULT_TARGET = 41
    }
}

@HiltViewModel
class NewTarneebMatchViewModel @Inject constructor(
    private val repository: LeekhaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewTarneebMatchUiState())
    val uiState: StateFlow<NewTarneebMatchUiState> = _uiState

    /**
     * Team names this account has used before, most recent first.
     *
     * Scoped to Tarneeb: Leekha's player names would be noise here, and the
     * same query keeps Tarneeb's team names out of Leekha's suggestions.
     */
    val knownTeams: StateFlow<List<String>> =
        repository.getPlayersForRule(ScoreRule.TARNEEB)
            .map { players -> players.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTeamName(index: Int, name: String) {
        _uiState.value = when (index) {
            0 -> _uiState.value.copy(teamAName = name)
            else -> _uiState.value.copy(teamBName = name)
        }
    }

    fun setTerminalScore(score: Int) {
        _uiState.value = _uiState.value.copy(terminalScore = score)
    }

    /** The first team slot with nothing in it, for the recent-team chips. */
    fun firstEmptySlot(): Int = _uiState.value.names.indexOfFirst { it.isBlank() }

    /** Suggestions for one slot, minus whatever the other slot already holds. */
    fun suggestionsFor(index: Int, all: List<String>): List<String> {
        val taken = _uiState.value.names
            .filterIndexed { i, name -> i != index && name.isNotBlank() }
            .map { it.trim().lowercase() }
        return all.filter { it.trim().lowercase() !in taken }
    }

    fun createMatch() {
        val state = _uiState.value
        if (!state.isComplete || state.isCreating) return

        _uiState.value = state.copy(isCreating = true)
        viewModelScope.launch {
            val id = repository.createTarneebMatch(
                teamAName = state.teamAName.trim(),
                teamBName = state.teamBName.trim(),
                terminalScore = state.terminalScore
            )
            _uiState.value = _uiState.value.copy(isCreating = false, createdMatchId = id)
        }
    }

    /** Cleared after navigating, so returning to this screen does not re-navigate. */
    fun onNavigated() {
        _uiState.value = _uiState.value.copy(createdMatchId = null)
    }
}

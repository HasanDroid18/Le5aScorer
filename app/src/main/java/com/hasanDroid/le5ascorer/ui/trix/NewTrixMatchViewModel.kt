package com.hasanDroid.le5ascorer.ui.trix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.repository.LeekhaRepository
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine
import com.hasanDroid.le5ascorer.domain.TrixSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Two steps on one screen: name the four seats and choose doubling, then say
 * who holds the 7 of Hearts. Seats 0+1 are Team A, 2+3 Team B.
 */
data class NewTrixMatchUiState(
    val names: List<String> = List(TrixScoreEngine.SEATS) { "" },
    val doubling: Boolean = false,
    val choosingOpener: Boolean = false,
    val openerSeat: Int? = null,
    val isCreating: Boolean = false,
    val createdMatchId: Long? = null
) {
    private val filled: List<String> get() = names.map { it.trim() }.filter { it.isNotEmpty() }

    val hasDuplicateNames: Boolean
        get() = filled.map { it.lowercase() }.toSet().size != filled.size

    val isComplete: Boolean get() = filled.size == names.size && !hasDuplicateNames

    val canStart: Boolean
        get() = !isCreating && if (choosingOpener) openerSeat != null else isComplete
}

@HiltViewModel
class NewTrixMatchViewModel @Inject constructor(
    private val repository: LeekhaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewTrixMatchUiState())
    val uiState: StateFlow<NewTrixMatchUiState> = _uiState

    /** Everyone who has played anything before, most recent first — the same people play Trix. */
    val knownPlayers: StateFlow<List<String>> = repository.getAllPlayers()
        .map { players -> players.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setName(seat: Int, name: String) = _uiState.update {
        it.copy(names = it.names.toMutableList().apply { set(seat, name) })
    }

    fun setDoubling(doubling: Boolean) = _uiState.update { it.copy(doubling = doubling) }

    fun setOpener(seat: Int) = _uiState.update { it.copy(openerSeat = seat) }

    fun backToNames() = _uiState.update { it.copy(choosingOpener = false) }

    /** Known players minus anyone already seated elsewhere. */
    fun suggestionsFor(seat: Int): List<String> {
        val taken = _uiState.value.names
            .filterIndexed { index, name -> index != seat && name.isNotBlank() }
            .map { it.trim().lowercase() }
            .toSet()
        return knownPlayers.value.filter { it.trim().lowercase() !in taken }
    }

    /** First press moves to the 7♥ question; the second creates the match. */
    fun start() {
        val state = _uiState.value
        if (!state.canStart) return
        if (!state.choosingOpener) {
            _uiState.update { it.copy(choosingOpener = true) }
            return
        }
        val opener = state.openerSeat ?: return

        _uiState.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            val id = repository.createTrixMatch(
                playerNames = state.names.map { it.trim() },
                setup = TrixSetup(openerSeat = opener, doubling = state.doubling)
            )
            _uiState.update { it.copy(isCreating = false, createdMatchId = id) }
        }
    }

    /** Cleared after navigating, so returning here does not navigate again. */
    fun onNavigated() = _uiState.update { it.copy(createdMatchId = null) }
}

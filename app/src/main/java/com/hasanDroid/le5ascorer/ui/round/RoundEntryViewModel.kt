package com.hasanDroid.le5ascorer.ui.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasanDroid.le5ascorer.data.local.entity.ActionType
import com.hasanDroid.le5ascorer.domain.ScoreEngine
import com.hasanDroid.le5ascorer.domain.model.ScoreAction
import com.hasanDroid.le5ascorer.domain.usecase.AddRoundUseCase
import com.hasanDroid.le5ascorer.domain.usecase.GetRoundUseCase
import com.hasanDroid.le5ascorer.domain.usecase.UpdateRoundUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerRoundData(
    val playerIndex: Int,
    val playerName: String,
    val heartCount: Int = 0,
    val qSpadesCount: Int = 0,
    val tenDiamondsCount: Int = 0,
    val total: Int = 0
)

data class RoundEntryUiState(
    val matchId: Long = 0,
    val roundId: Long? = null,
    val playerNames: List<String> = emptyList(),
    val playersData: List<PlayerRoundData> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false
)

@HiltViewModel
class RoundEntryViewModel @Inject constructor(
    private val addRoundUseCase: AddRoundUseCase,
    private val updateRoundUseCase: UpdateRoundUseCase,
    private val getRoundUseCase: GetRoundUseCase,
    private val scoreEngine: ScoreEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoundEntryUiState())
    val uiState: StateFlow<RoundEntryUiState> = _uiState.asStateFlow()

    fun initialize(matchId: Long, playerNames: List<String>, roundId: Long? = null) {
        val playersData = playerNames.mapIndexed { index, name ->
            PlayerRoundData(playerIndex = index, playerName = name)
        }

        _uiState.value = _uiState.value.copy(
            matchId = matchId,
            roundId = roundId,
            playerNames = playerNames,
            playersData = playersData
        )

        // Load existing round data if editing
        if (roundId != null) {
            loadRound(roundId)
        }
    }

    private fun loadRound(roundId: Long) {
        viewModelScope.launch {
            val round = getRoundUseCase(roundId) ?: return@launch

            val playersData = _uiState.value.playersData.map { player ->
                val actions = round.actions.filter { it.receiverIndex == player.playerIndex }

                // Check if this player has a DOUBLE (37 points saved as single Q_SPADES action with delta=37)
                val doubleAction = actions.find { it.actionType == ActionType.Q_SPADES && it.delta == 37 }

                if (doubleAction != null) {
                    // This is a DOUBLE round - restore the 37 points with visual indicators
                    player.copy(
                        heartCount = 0,
                        qSpadesCount = 1,        // Visual indicator
                        tenDiamondsCount = 1,    // Visual indicator
                        total = 37               // The actual double score
                    )
                } else {
                    // Normal round - load individual card counts
                    val heartCount = actions.filter { it.actionType == ActionType.HEART }.sumOf { it.delta }
                    val qSpadesCount = actions.count { it.actionType == ActionType.Q_SPADES }
                    val tenDiamondsCount = actions.count { it.actionType == ActionType.TEN_DIAMONDS }

                    player.copy(
                        heartCount = heartCount,
                        qSpadesCount = qSpadesCount,
                        tenDiamondsCount = tenDiamondsCount,
                        total = calculatePlayerTotal(heartCount, qSpadesCount, tenDiamondsCount)
                    )
                }
            }

            _uiState.value = _uiState.value.copy(playersData = playersData)
        }
    }

    fun incrementHeart(playerIndex: Int) {
        val playersData = _uiState.value.playersData

        val totalHearts = playersData.sumOf { it.heartCount }
        if (totalHearts >= 13) {
            return
        }

        // Check if we're in a double state (one player has 37, others have 0)
        val totalRound = playersData.sumOf { it.total }
        val doublePlayer = playersData.indexOfFirst { it.total == 37 }
        val isDouble = totalRound == 37 && doublePlayer != -1

        if (isDouble && playerIndex != doublePlayer) {
            // Another player is adding hearts while in double state
            // Cancel the double: reset the double player to 0 (discard everything)
            val updatedPlayers = playersData.map { player ->
                if (player.playerIndex == doublePlayer) {
                    // Reset double player to 0 (discard the double completely)
                    player.copy(
                        heartCount = 0,
                        qSpadesCount = 0,
                        tenDiamondsCount = 0,
                        total = 0
                    )
                } else {
                    player
                }
            }
            _uiState.value = _uiState.value.copy(playersData = updatedPlayers)
        }

        updatePlayerData(playerIndex) { player ->
            val newCount = player.heartCount + 1
            player.copy(
                heartCount = newCount,
                total = calculatePlayerTotal(newCount, player.qSpadesCount, player.tenDiamondsCount)
            )
        }
    }

    fun decrementHeart(playerIndex: Int) {
        updatePlayerData(playerIndex) { player ->
            if (player.heartCount > 0) {
                val newCount = player.heartCount - 1
                player.copy(
                    heartCount = newCount,
                    total = calculatePlayerTotal(newCount, player.qSpadesCount, player.tenDiamondsCount)
                )
            } else {
                player
            }
        }
    }

    fun toggleTenDiamonds(playerIndex: Int) {
        // Check if we're in a double state (one player has 37, others have 0)
        val playersData = _uiState.value.playersData
        val totalRound = playersData.sumOf { it.total }
        val doublePlayer = playersData.indexOfFirst { it.total == 37 }
        val isDouble = totalRound == 37 && doublePlayer != -1

        if (isDouble && playerIndex != doublePlayer) {
            // Another player is taking 10♦ while in double state
            // Cancel the double: reset the double player to 0 (discard everything)
            val updatedPlayers = playersData.map { player ->
                if (player.playerIndex == doublePlayer) {
                    // Reset double player to 0 (discard the double completely)
                    player.copy(
                        heartCount = 0,
                        qSpadesCount = 0,
                        tenDiamondsCount = 0,
                        total = 0
                    )
                } else {
                    player
                }
            }
            _uiState.value = _uiState.value.copy(playersData = updatedPlayers)
        }

        // Only one player can have 10 Denare
        val currentHolder = playersData.indexOfFirst { it.tenDiamondsCount > 0 }
        if (currentHolder != -1 && currentHolder != playerIndex) {
            // Remove from current holder first
            updatePlayerData(currentHolder) { player ->
                player.copy(
                    tenDiamondsCount = 0,
                    total = calculatePlayerTotal(player.heartCount, player.qSpadesCount, 0)
                )
            }
        }

        updatePlayerData(playerIndex) { player ->
            val newCount = if (player.tenDiamondsCount > 0) 0 else 1 // Toggle
            player.copy(
                tenDiamondsCount = newCount,
                total = calculatePlayerTotal(player.heartCount, player.qSpadesCount, newCount)
            )
        }
    }

    fun incrementTenDiamonds(playerIndex: Int) = toggleTenDiamonds(playerIndex)

    fun toggleQSpades(playerIndex: Int) {
        // Check if we're in a double state (one player has 37, others have 0)
        val playersData = _uiState.value.playersData
        val totalRound = playersData.sumOf { it.total }
        val doublePlayer = playersData.indexOfFirst { it.total == 37 }
        val isDouble = totalRound == 37 && doublePlayer != -1

        if (isDouble && playerIndex != doublePlayer) {
            // Another player is taking Q♠ while in double state
            // Cancel the double: reset the double player to 0 (discard everything)
            val updatedPlayers = playersData.map { player ->
                if (player.playerIndex == doublePlayer) {
                    // Reset double player to 0 (discard the double completely)
                    player.copy(
                        heartCount = 0,
                        qSpadesCount = 0,
                        tenDiamondsCount = 0,
                        total = 0
                    )
                } else {
                    player
                }
            }
            _uiState.value = _uiState.value.copy(playersData = updatedPlayers)
        }

        // Only one player can have Bnt Baston (Q Spades)
        val currentHolder = playersData.indexOfFirst { it.qSpadesCount > 0 }
        if (currentHolder != -1 && currentHolder != playerIndex) {
            // Remove from current holder first
            updatePlayerData(currentHolder) { player ->
                player.copy(
                    qSpadesCount = 0,
                    total = calculatePlayerTotal(player.heartCount, 0, player.tenDiamondsCount)
                )
            }
        }

        updatePlayerData(playerIndex) { player ->
            val newCount = if (player.qSpadesCount > 0) 0 else 1 // Toggle
            player.copy(
                qSpadesCount = newCount,
                total = calculatePlayerTotal(player.heartCount, newCount, player.tenDiamondsCount)
            )
        }
    }

    fun incrementQSpades(playerIndex: Int) = toggleQSpades(playerIndex)

    fun setDouble(playerIndex: Int) {
        // DOUBLE button pressed: Set 37 points for this player, 0 for all others
        // NOTE: The qSpadesCount and tenDiamondsCount are just visual indicators
        // The 37 points come from pressing DOUBLE button, NOT from having both cards
        // This is the ONLY way to create a 37-point round

        val updatedPlayers = _uiState.value.playersData.mapIndexed { index, player ->
            if (index == playerIndex) {
                player.copy(
                    heartCount = 0,
                    qSpadesCount = 1,        // Visual indicator only
                    tenDiamondsCount = 1,    // Visual indicator only
                    total = 37               // THIS is what creates the double
                )
            } else {
                player.copy(
                    heartCount = 0,
                    qSpadesCount = 0,
                    tenDiamondsCount = 0,
                    total = 0
                )
            }
        }
        _uiState.value = _uiState.value.copy(playersData = updatedPlayers)
    }

    fun reset() {
        // Reset all players' scores to 0
        val resetPlayers = _uiState.value.playersData.map { player ->
            player.copy(
                heartCount = 0,
                qSpadesCount = 0,
                tenDiamondsCount = 0,
                total = 0
            )
        }
        _uiState.value = _uiState.value.copy(playersData = resetPlayers)
    }



    private fun updatePlayerData(playerIndex: Int, update: (PlayerRoundData) -> PlayerRoundData) {
        val updatedPlayers = _uiState.value.playersData.map { player ->
            if (player.playerIndex == playerIndex) update(player) else player
        }
        _uiState.value = _uiState.value.copy(playersData = updatedPlayers)
    }

    private fun calculatePlayerTotal(hearts: Int, qSpades: Int, tenDiamonds: Int): Int {
        return scoreEngine.getScoreForActionType(ActionType.HEART, hearts) +
                scoreEngine.getScoreForActionType(ActionType.Q_SPADES, qSpades) +
                scoreEngine.getScoreForActionType(ActionType.TEN_DIAMONDS, tenDiamonds)
    }

    fun saveRound() {
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val actions = mutableListOf<ScoreAction>()

                // Check if this is a double round (total = 37 for one player, 0 for others)
                val totalRound = state.playersData.sumOf { it.total }
                val isDouble = totalRound == 37

                state.playersData.forEach { player ->
                    if (isDouble && player.total == 37) {
                        // For double rounds: save the full 37 as a single action
                        // The Q♠ and 10♦ counts are just visual markers
                        actions.add(
                            ScoreAction(
                                roundId = state.roundId ?: 0,
                                receiverIndex = player.playerIndex,
                                actionType = ActionType.Q_SPADES, // Using Q_SPADES as marker for double
                                delta = 37  // The actual 37 points from DOUBLE button
                            )
                        )
                    } else {
                        // Normal round: save individual actions as usual
                        // Add heart actions
                        if (player.heartCount > 0) {
                            actions.add(
                                ScoreAction(
                                    roundId = state.roundId ?: 0,
                                    receiverIndex = player.playerIndex,
                                    actionType = ActionType.HEART,
                                    delta = player.heartCount
                                )
                            )
                        }
                        // Add Q Spades actions
                        repeat(player.qSpadesCount) {
                            actions.add(
                                ScoreAction(
                                    roundId = state.roundId ?: 0,
                                    receiverIndex = player.playerIndex,
                                    actionType = ActionType.Q_SPADES,
                                    delta = 13
                                )
                            )
                        }
                        // Add 10 Diamonds actions
                        repeat(player.tenDiamondsCount) {
                            actions.add(
                                ScoreAction(
                                    roundId = state.roundId ?: 0,
                                    receiverIndex = player.playerIndex,
                                    actionType = ActionType.TEN_DIAMONDS,
                                    delta = 10
                                )
                            )
                        }
                    }
                }

                if (state.roundId != null) {
                    // Update existing round
                    updateRoundUseCase(state.roundId, actions)
                } else {
                    // Add new round
                    addRoundUseCase(state.matchId, actions)
                }

                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }
}


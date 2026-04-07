package com.hasanDroid.le5ascorer.ui.round

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentRoundEntryBinding
import com.hasanDroid.le5ascorer.databinding.ItemPlayerRowBinding
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RoundEntryFragment : Fragment() {

    private var _binding: FragmentRoundEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RoundEntryViewModel by viewModels()
    private val args: RoundEntryFragmentArgs by navArgs()

    private var selectedPlayerIndex: Int = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoundEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val playerNames = args.playerNames.toList()
        val roundId = if (args.roundId == -1L) null else args.roundId

        viewModel.initialize(args.matchId, playerNames, roundId)

        setupToolbar()
        setupPlayerRows()
        setupCardActions()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.title = if (args.roundId == -1L)
            getString(R.string.select_cards)
        else
            getString(R.string.edit_round)

        binding.buttonReset.setOnClickListener {
            viewModel.reset()
        }
    }

    private fun setupPlayerRows() {
        // Player 1 click
        binding.rowPlayer1.cardPlayerRow.setOnClickListener {
            selectedPlayerIndex = 0
            updatePlayerRowSelection()
        }

        // Player 2 click
        binding.rowPlayer2.cardPlayerRow.setOnClickListener {
            selectedPlayerIndex = 1
            updatePlayerRowSelection()
        }

        // Player 3 click
        binding.rowPlayer3.cardPlayerRow.setOnClickListener {
            selectedPlayerIndex = 2
            updatePlayerRowSelection()
        }

        // Player 4 click
        binding.rowPlayer4.cardPlayerRow.setOnClickListener {
            selectedPlayerIndex = 3
            updatePlayerRowSelection()
        }
    }

    private fun setupCardActions() {
        // Hearts card - tap to add 1, long press to add 5
        binding.cardHearts.setOnClickListener {
            performCardAction {
                viewModel.incrementHeart(selectedPlayerIndex)
            }
        }

        binding.cardHearts.setOnLongClickListener {
            performCardAction {
                // Add 5 hearts quickly
                repeat(5) {
                    viewModel.incrementHeart(selectedPlayerIndex)
                }
            }
            true
        }

        // Q Spades card
        binding.cardQSpades.setOnClickListener {
            performCardAction {
                viewModel.toggleQSpades(selectedPlayerIndex)
            }
        }

        // 10 Diamonds card
        binding.cardTenDiamonds.setOnClickListener {
            performCardAction {
                viewModel.toggleTenDiamonds(selectedPlayerIndex)
            }
        }

        // Double card
        binding.cardDouble.setOnClickListener {
            performCardAction {
                viewModel.setDouble(selectedPlayerIndex)
            }
        }

        // Save button
        binding.buttonSave.setOnClickListener {
            viewModel.saveRound()
        }
    }

    private fun performCardAction(action: () -> Unit) {
        action()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updatePlayerRows(state)
                    updateStatusCard(state)
                    updateActionChips(state)
                    updateSaveButton(state)

                    if (state.saved) {
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun updatePlayerRows(state: RoundEntryUiState) {
        state.playersData.forEachIndexed { index, player ->
            val rowBinding = when (index) {
                0 -> binding.rowPlayer1
                1 -> binding.rowPlayer2
                2 -> binding.rowPlayer3
                3 -> binding.rowPlayer4
                else -> return@forEachIndexed
            }

            updatePlayerRow(rowBinding, player, index == selectedPlayerIndex)
        }
    }

    private fun updatePlayerRow(rowBinding: ItemPlayerRowBinding, player: PlayerRoundData, isSelected: Boolean) {
        // Update player info
        rowBinding.textPlayerName.text = player.playerName
        rowBinding.textPlayerScore.text = player.total.toString()

        // Update card indicators
        if (player.heartCount > 0) {
            rowBinding.layoutHearts.visibility = View.VISIBLE
            rowBinding.textHeartCount.text = getString(R.string.heart_count_format, player.heartCount)
        } else {
            rowBinding.layoutHearts.visibility = View.GONE
        }

        rowBinding.iconQSpades.visibility = if (player.qSpadesCount > 0) View.VISIBLE else View.GONE
        rowBinding.iconTenDiamonds.visibility = if (player.tenDiamondsCount > 0) View.VISIBLE else View.GONE

        // Update selection state
        if (isSelected) {
            rowBinding.cardPlayerRow.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.chip_gold)
            )
            rowBinding.cardPlayerRow.strokeWidth = 6
            rowBinding.cardPlayerRow.strokeColor =
                ContextCompat.getColor(requireContext(), R.color.chip_gold_dark)
            rowBinding.cardPlayerRow.elevation = 12f
        } else {
            rowBinding.cardPlayerRow.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.chip_silver)
            )
            rowBinding.cardPlayerRow.strokeWidth = 2
            rowBinding.cardPlayerRow.strokeColor =
                ContextCompat.getColor(requireContext(), R.color.chip_gold_dark)
            rowBinding.cardPlayerRow.elevation = 4f
        }
    }

    private fun updatePlayerRowSelection() {
        // Just trigger a full UI update
        viewModel.uiState.value.let { state ->
            updatePlayerRows(state)
        }
    }

    private fun updateStatusCard(state: RoundEntryUiState) {
        val totalHearts = state.playersData.sumOf { it.heartCount }
        val totalRound = state.playersData.sumOf { it.total }

        val qOwnerIndex = state.playersData.indexOfFirst { it.qSpadesCount > 0 }
        val tenOwnerIndex = state.playersData.indexOfFirst { it.tenDiamondsCount > 0 }

        val doublePlayerIndex = state.playersData.indexOfFirst { it.total == 37 }
        val isDouble = totalRound == 37 && doublePlayerIndex != -1

        // Update progress and total
        binding.textRoundTotal.text = getString(R.string.round_total_format, totalRound)
        binding.progressRound.progress = totalRound

        if (isDouble) {
            binding.progressRound.max = 37
            binding.textRoundTotal.text = getString(R.string.round_total_format_double, totalRound)
        } else {
            binding.progressRound.max = 36
        }

        // Hearts remaining
        binding.textHeartsRemaining.text = getString(R.string.hearts_remaining, totalHearts)

        // Q and 10 assignment
        binding.textQAssigned.text = if (qOwnerIndex != -1) {
            "Q♠: ${state.playersData[qOwnerIndex].playerName}"
        } else {
            "Q♠: ${getString(R.string.not_assigned)}"
        }

        binding.textTenAssigned.text = if (tenOwnerIndex != -1) {
            "10♦: ${state.playersData[tenOwnerIndex].playerName}"
        } else {
            "10♦: ${getString(R.string.not_assigned)}"
        }

        // Double status
        if (isDouble) {
            // Show ONLY Double badge, hide normal status badges
            binding.layoutDoubleStatus.visibility = View.VISIBLE
            binding.layoutNormalStatus.visibility = View.GONE
            binding.cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.danger_red_700)
            )
        } else {
            // Show normal status badges, hide Double badge
            binding.layoutDoubleStatus.visibility = View.GONE
            binding.layoutNormalStatus.visibility = View.VISIBLE

            // Check if valid
            val isValid = qOwnerIndex != -1 && tenOwnerIndex != -1 &&
                         totalHearts == 13 && totalRound == 36

            if (isValid) {
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.status_valid_bg)
                )
                binding.textStatusTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_valid_text)
                )
            } else {
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.status_invalid_bg)
                )
                binding.textStatusTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_invalid_text)
                )
            }
        }
    }

    private fun updateActionChips(state: RoundEntryUiState) {
        binding.chipGroupActions.removeAllViews()

        state.playersData.forEach { player ->
            // Add hearts chip if player has hearts
            if (player.heartCount > 0) {
                val chip = createActionChip(
                    "${player.playerName}: ♥ ×${player.heartCount}",
                    player.playerIndex,
                    ActionType.HEARTS
                )
                binding.chipGroupActions.addView(chip)
            }

            // Add Q Spades chip
            if (player.qSpadesCount > 0) {
                val chip = createActionChip(
                    "${player.playerName}: Q♠",
                    player.playerIndex,
                    ActionType.Q_SPADES
                )
                binding.chipGroupActions.addView(chip)
            }

            // Add 10 Diamonds chip
            if (player.tenDiamondsCount > 0) {
                val chip = createActionChip(
                    "${player.playerName}: 10♦",
                    player.playerIndex,
                    ActionType.TEN_DIAMONDS
                )
                binding.chipGroupActions.addView(chip)
            }
        }
    }

    private fun createActionChip(
        text: String,
        playerIndex: Int,
        actionType: ActionType
    ): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCloseIconVisible = true
            closeIconTint = ContextCompat.getColorStateList(requireContext(), android.R.color.black)
            setOnCloseIconClickListener {
                when (actionType) {
                    ActionType.HEARTS -> viewModel.decrementHeart(playerIndex)
                    ActionType.Q_SPADES -> viewModel.toggleQSpades(playerIndex)
                    ActionType.TEN_DIAMONDS -> viewModel.toggleTenDiamonds(playerIndex)
                }
            }
        }
    }

    private fun updateSaveButton(state: RoundEntryUiState) {
        val totalHearts = state.playersData.sumOf { it.heartCount }
        val totalRound = state.playersData.sumOf { it.total }

        val qOwnerIndex = state.playersData.indexOfFirst { it.qSpadesCount > 0 }
        val tenOwnerIndex = state.playersData.indexOfFirst { it.tenDiamondsCount > 0 }

        val doublePlayerIndex = state.playersData.indexOfFirst { it.total == 37 }
        val isDouble = totalRound == 37 && doublePlayerIndex != -1

        val isValid = if (isDouble) {
            true // Double is automatically valid if total is 37
        } else {
            qOwnerIndex != -1 && tenOwnerIndex != -1 &&
            totalHearts == 13 && totalRound == 36
        }

        binding.buttonSave.isEnabled = !state.isSaving && isValid

        if (isValid) {
            binding.buttonSave.icon = ContextCompat.getDrawable(
                requireContext(),
                android.R.drawable.ic_menu_save
            )
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class ActionType {
        HEARTS, Q_SPADES, TEN_DIAMONDS
    }
}


package com.hasanDroid.le5ascorer.ui.round

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentRoundEntryBinding
import com.hasanDroid.le5ascorer.databinding.ItemPlayerRowBinding
import com.hasanDroid.le5ascorer.databinding.ViewCheckRowBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val NO_ROUND = -1L
private const val REQUIRED_HEARTS = 13
private const val REQUIRED_TOTAL = 36
private const val DOUBLE_TOTAL = 37
private const val HEARTS_LONG_PRESS_STEP = 5

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
        val roundId = if (args.roundId == NO_ROUND) null else args.roundId

        viewModel.initialize(args.matchId, playerNames, roundId)

        applyInsets()
        setupToolbar()
        setupPlayerRows()
        setupCardActions()
        observeUiState()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.buttonSave.applySystemBarInsets(bottom = true, sides = false)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.title = getString(
            if (args.roundId == NO_ROUND) R.string.select_cards else R.string.edit_round
        )
        binding.buttonReset.setOnClickListener { viewModel.reset() }
    }

    private fun setupPlayerRows() {
        playerRowBindings().forEachIndexed { index, rowBinding ->
            rowBinding.cardPlayerRow.setOnClickListener {
                selectedPlayerIndex = index
                render(viewModel.uiState.value)
            }
        }
    }

    private fun playerRowBindings(): List<ItemPlayerRowBinding> = listOf(
        binding.rowPlayer1, binding.rowPlayer2, binding.rowPlayer3, binding.rowPlayer4
    )

    private fun setupCardActions() {
        binding.cardHearts.setOnClickListener { viewModel.incrementHeart(selectedPlayerIndex) }
        binding.cardHearts.setOnLongClickListener {
            repeat(HEARTS_LONG_PRESS_STEP) { viewModel.incrementHeart(selectedPlayerIndex) }
            true
        }
        binding.cardQSpades.setOnClickListener { viewModel.toggleQSpades(selectedPlayerIndex) }
        binding.cardTenDiamonds.setOnClickListener {
            viewModel.toggleTenDiamonds(selectedPlayerIndex)
        }
        binding.cardDouble.setOnClickListener { viewModel.setDouble(selectedPlayerIndex) }
        binding.buttonSave.setOnClickListener { viewModel.saveRound() }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                    if (state.saved) findNavController().navigateUp()
                }
            }
        }
    }

    private fun render(state: RoundEntryUiState) {
        val validity = Validity.of(state)
        updatePlayerRows(state)
        updateScoringFor(state)
        updateStatusCard(state, validity)
        updateActionChips(state)
        binding.buttonSave.isEnabled = !state.isSaving && validity.isValid
    }

    // ================================================================
    // Validity
    // ================================================================

    /**
     * The round's completeness, derived once per render. The same four
     * conditions previously got recomputed independently in the status card and
     * in the save-button handler, which is how they drifted apart.
     */
    private data class Validity(
        val totalHearts: Int,
        val totalRound: Int,
        val qOwnerIndex: Int,
        val tenOwnerIndex: Int,
        val isDouble: Boolean
    ) {
        val heartsComplete get() = totalHearts == REQUIRED_HEARTS
        val hasQueen get() = qOwnerIndex != -1
        val hasTen get() = tenOwnerIndex != -1
        val target get() = if (isDouble) DOUBLE_TOTAL else REQUIRED_TOTAL

        // A Double is self-validating: one player holding all 37 points is a
        // complete round by definition.
        val isValid: Boolean
            get() = isDouble ||
                (hasQueen && hasTen && heartsComplete && totalRound == REQUIRED_TOTAL)

        companion object {
            fun of(state: RoundEntryUiState): Validity {
                val players = state.playersData
                val totalRound = players.sumOf { it.total }
                return Validity(
                    totalHearts = players.sumOf { it.heartCount },
                    totalRound = totalRound,
                    qOwnerIndex = players.indexOfFirst { it.qSpadesCount > 0 },
                    tenOwnerIndex = players.indexOfFirst { it.tenDiamondsCount > 0 },
                    isDouble = totalRound == DOUBLE_TOTAL &&
                        players.any { it.total == DOUBLE_TOTAL }
                )
            }
        }
    }

    // ================================================================
    // Rendering
    // ================================================================

    private fun updateScoringFor(state: RoundEntryUiState) {
        binding.textScoringFor.text = state.playersData
            .getOrNull(selectedPlayerIndex)
            ?.playerName
            ?: getString(R.string.player_default)
    }

    private fun updatePlayerRows(state: RoundEntryUiState) {
        val rows = playerRowBindings()
        state.playersData.forEachIndexed { index, player ->
            rows.getOrNull(index)?.let {
                updatePlayerRow(it, player, isSelected = index == selectedPlayerIndex)
            }
        }
    }

    private fun updatePlayerRow(
        row: ItemPlayerRowBinding,
        player: PlayerRoundData,
        isSelected: Boolean
    ) {
        row.textPlayerName.text = player.playerName
        row.textPlayerScore.text = player.total.toString()

        row.layoutHearts.showIf(player.heartCount > 0)
        row.textHeartCount.text = getString(R.string.heart_count_format, player.heartCount)
        row.layoutQSpades.showIf(player.qSpadesCount > 0)
        row.layoutTenDiamonds.showIf(player.tenDiamondsCount > 0)

        row.cardPlayerRow.contentDescription =
            getString(R.string.cd_select_player, player.playerName)

        // Selection flips the whole tile from felt to brass. Stroke and
        // elevation come from dimen resources because MaterialCardView takes
        // pixels, not dp — the previous literals (6, 12f) rendered thinner on
        // high-density screens and thicker on low.
        val res = resources
        if (isSelected) {
            row.cardPlayerRow.setCardBackgroundColor(color(R.color.brass_400))
            row.cardPlayerRow.strokeColor = color(R.color.brass_600)
            row.cardPlayerRow.strokeWidth = res.getDimensionPixelSize(R.dimen.stroke_selected)
            row.cardPlayerRow.cardElevation = res.getDimension(R.dimen.elev_float)
        } else {
            row.cardPlayerRow.setCardBackgroundColor(color(R.color.felt_700))
            row.cardPlayerRow.strokeColor = color(R.color.felt_outline)
            row.cardPlayerRow.strokeWidth = res.getDimensionPixelSize(R.dimen.stroke_hairline)
            row.cardPlayerRow.cardElevation = res.getDimension(R.dimen.elev_raised)
        }

        // On brass the tile reads as a printed card, so the suits take their
        // printed colours. On felt, printed red would fall below a usable
        // contrast ratio, so the indicators go monochrome — the suit shapes
        // still carry the meaning.
        val onTile = if (isSelected) R.color.felt_950 else R.color.text_primary
        val suitRed = if (isSelected) R.color.ink_red else R.color.text_secondary
        val suitBlack = if (isSelected) R.color.ink else R.color.text_secondary

        row.textPlayerName.setTextColor(color(onTile))
        row.textPlayerScore.setTextColor(color(onTile))
        row.iconHearts.tint(suitRed)
        row.textHeartCount.setTextColor(color(suitRed))
        row.textQSpadesLabel.setTextColor(color(suitBlack))
        row.iconQSpades.tint(suitBlack)
        row.textTenLabel.setTextColor(color(suitRed))
        row.iconTenDiamonds.tint(suitRed)
    }

    private fun updateStatusCard(state: RoundEntryUiState, validity: Validity) {
        binding.textRoundTotal.text =
            getString(R.string.round_total_format, validity.totalRound, validity.target)
        binding.progressRound.max = validity.target
        binding.progressRound.progress = validity.totalRound

        binding.layoutDoubleStatus.showIf(validity.isDouble)
        binding.layoutNormalStatus.showIf(!validity.isDouble)

        val accent = when {
            validity.isDouble -> R.color.danger
            validity.isValid -> R.color.success
            else -> R.color.brass_400
        }
        binding.progressRound.setIndicatorColor(color(accent))
        binding.textRoundTotal.setTextColor(color(accent))

        if (validity.isDouble) return

        bindCheck(
            binding.checkHearts,
            met = validity.heartsComplete,
            label = getString(R.string.check_hearts, validity.totalHearts, REQUIRED_HEARTS)
        )
        bindCheck(
            binding.checkQueen,
            met = validity.hasQueen,
            label = ownerLabel(
                state, validity.qOwnerIndex, R.string.check_queen_to, R.string.check_queen
            )
        )
        bindCheck(
            binding.checkTen,
            met = validity.hasTen,
            label = ownerLabel(
                state, validity.tenOwnerIndex, R.string.check_ten_to, R.string.check_ten
            )
        )
    }

    private fun ownerLabel(
        state: RoundEntryUiState,
        ownerIndex: Int,
        assignedRes: Int,
        unassignedRes: Int
    ): String {
        val owner = state.playersData.getOrNull(ownerIndex)
        return if (owner != null) getString(assignedRes, owner.playerName)
        else getString(unassignedRes)
    }

    private fun bindCheck(row: ViewCheckRowBinding, met: Boolean, label: String) {
        row.textCheck.text = label
        row.iconCheck.setImageResource(
            if (met) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
        )
        row.iconCheck.tint(if (met) R.color.success else R.color.text_tertiary)
        row.textCheck.setTextColor(color(if (met) R.color.text_primary else R.color.text_tertiary))
    }

    private fun updateActionChips(state: RoundEntryUiState) {
        binding.chipGroupActions.removeAllViews()

        state.playersData.forEach { player ->
            if (player.heartCount > 0) {
                addActionChip(
                    getString(R.string.chip_hearts, player.playerName, player.heartCount),
                    R.drawable.ic_suit_heart
                ) { viewModel.decrementHeart(player.playerIndex) }
            }
            if (player.qSpadesCount > 0) {
                addActionChip(
                    getString(R.string.chip_queen, player.playerName, getString(R.string.label_queen)),
                    R.drawable.ic_suit_spade
                ) { viewModel.toggleQSpades(player.playerIndex) }
            }
            if (player.tenDiamondsCount > 0) {
                addActionChip(
                    getString(R.string.chip_ten, player.playerName, getString(R.string.label_ten)),
                    R.drawable.ic_suit_diamond
                ) { viewModel.toggleTenDiamonds(player.playerIndex) }
            }
        }
    }

    private fun addActionChip(label: String, iconRes: Int, onRemove: () -> Unit) {
        val chip = Chip(requireContext()).apply {
            setChipDrawable(
                com.google.android.material.chip.ChipDrawable.createFromAttributes(
                    requireContext(), null, 0, R.style.Widget_Le5a_Chip_Action
                )
            )
            text = label
            setChipIconResource(iconRes)
            isChipIconVisible = true
            isCloseIconVisible = true
            closeIconContentDescription = getString(R.string.cd_remove_action)
            setOnCloseIconClickListener { onRemove() }
        }
        binding.chipGroupActions.addView(chip)
    }

    // ================================================================

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun View.showIf(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun ImageView.tint(@ColorRes res: Int) {
        imageTintList = ColorStateList.valueOf(color(res))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

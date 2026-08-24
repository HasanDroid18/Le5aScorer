package com.hasanDroid.le5ascorer.ui.round

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.button.MaterialButton
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentRoundEntryBinding
import com.hasanDroid.le5ascorer.databinding.ViewHeartPipBinding
import com.hasanDroid.le5ascorer.databinding.ViewPlayerScoringRowBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val NO_ROUND = -1L
private const val REQUIRED_HEARTS = 13
private const val REQUIRED_TOTAL = 36
private const val DOUBLE_TOTAL = 37
private const val HEARTS_LONG_PRESS_STEP = 5

/**
 * Round entry.
 *
 * Every button belongs to the player whose row it sits in, so there is no
 * "select a player first" mode to get wrong: tapping Ahmad's ♥ gives Ahmad a
 * heart. The fanned pool at the top empties as hearts are dealt and the two
 * cards below name who holds them, which is why this screen needs no separate
 * validity checklist — what is missing is visible.
 */
@AndroidEntryPoint
class RoundEntryFragment : Fragment() {

    private var _binding: FragmentRoundEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RoundEntryViewModel by viewModels()
    private val args: RoundEntryFragmentArgs by navArgs()

    /** The thirteen cards in the pool, built once and then re-tinted. */
    private val heartPips = mutableListOf<ViewHeartPipBinding>()

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

        viewModel.initialize(
            args.matchId,
            args.playerNames.toList(),
            if (args.roundId == NO_ROUND) null else args.roundId
        )

        applyInsets()
        setupToolbar()
        buildHeartPool()
        setupPlayerRows()
        binding.buttonSave.setOnClickListener { viewModel.saveRound() }
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

    /** Thirteen cards, overlapped by a negative margin so they read as a fan. */
    private fun buildHeartPool() {
        val inflater = LayoutInflater.from(requireContext())
        val overlap = resources.getDimensionPixelSize(R.dimen.heart_pip_overlap)
        repeat(REQUIRED_HEARTS) { index ->
            val pip = ViewHeartPipBinding.inflate(inflater, binding.layoutHeartPool, false)
            (pip.root.layoutParams as LinearLayout.LayoutParams).apply {
                if (index > 0) marginStart = overlap
            }
            binding.layoutHeartPool.addView(pip.root)
            heartPips += pip
        }
    }

    private fun rows(): List<ViewPlayerScoringRowBinding> =
        listOf(binding.rowPlayer1, binding.rowPlayer2, binding.rowPlayer3, binding.rowPlayer4)

    private fun setupPlayerRows() {
        rows().forEachIndexed { seat, row ->
            row.buttonRowHearts.setOnClickListener { viewModel.incrementHeart(seat) }
            row.buttonRowHearts.setOnLongClickListener {
                repeat(HEARTS_LONG_PRESS_STEP) { viewModel.incrementHeart(seat) }
                true
            }
            row.buttonRowQueen.setOnClickListener { viewModel.toggleQSpades(seat) }
            row.buttonRowTen.setOnClickListener { viewModel.toggleTenDiamonds(seat) }
            row.buttonRowDouble.setOnClickListener { viewModel.setDouble(seat) }
        }
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
        updateHeartPool(validity)
        updateHolders(state, validity)
        updatePlayerRows(state, validity)

        binding.textDoubleNote.showIf(validity.isDouble)
        binding.textRoundTotal.text =
            getString(R.string.round_total_format, validity.totalRound, validity.target)
        binding.textRoundTotal.setTextColor(
            color(if (validity.isValid) R.color.success else R.color.text_tertiary)
        )
        binding.buttonSave.isEnabled = !state.isSaving && validity.isValid
    }

    // ================================================================
    // Validity
    // ================================================================

    /** The round's completeness, derived once per render. */
    private data class Validity(
        val totalHearts: Int,
        val totalRound: Int,
        val qOwnerIndex: Int,
        val tenOwnerIndex: Int,
        val isDouble: Boolean
    ) {
        val heartsComplete get() = totalHearts == REQUIRED_HEARTS
        val target get() = if (isDouble) DOUBLE_TOTAL else REQUIRED_TOTAL

        // A Double is self-validating: one player holding all 37 points is a
        // complete round by definition.
        val isValid: Boolean
            get() = isDouble ||
                (qOwnerIndex != -1 && tenOwnerIndex != -1 &&
                    heartsComplete && totalRound == REQUIRED_TOTAL)

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

    /** Dealt hearts fade out of the fan, so what is left is the pool. */
    private fun updateHeartPool(validity: Validity) {
        heartPips.forEachIndexed { index, pip ->
            val dealt = index < validity.totalHearts
            pip.root.alpha = if (dealt) 0.22f else 1f
            pip.root.cardElevation = resources.getDimension(
                if (dealt) R.dimen.elev_flat else R.dimen.elev_raised
            )
        }
        binding.textHeartsRemaining.text =
            getString(R.string.check_hearts, validity.totalHearts, REQUIRED_HEARTS)
        binding.textHeartsRemaining.setTextColor(
            color(if (validity.heartsComplete) R.color.success else R.color.text_secondary)
        )
    }

    private fun updateHolders(state: RoundEntryUiState, validity: Validity) {
        binding.textQueenHolder.text =
            state.playersData.getOrNull(validity.qOwnerIndex)?.playerName
                ?: getString(R.string.unassigned)
        binding.textTenHolder.text =
            state.playersData.getOrNull(validity.tenOwnerIndex)?.playerName
                ?: getString(R.string.unassigned)

        binding.textQueenHolder.setTextColor(
            color(if (validity.qOwnerIndex != -1) R.color.brass_300 else R.color.text_tertiary)
        )
        binding.textTenHolder.setTextColor(
            color(if (validity.tenOwnerIndex != -1) R.color.brass_300 else R.color.text_tertiary)
        )
    }

    private fun updatePlayerRows(state: RoundEntryUiState, validity: Validity) {
        rows().forEachIndexed { seat, row ->
            val player = state.playersData.getOrNull(seat)
            if (player == null) {
                row.root.visibility = View.GONE
                return@forEachIndexed
            }
            row.root.visibility = View.VISIBLE
            row.textRowName.text = player.playerName
            row.textRowTotal.text = player.total.toString()

            row.buttonRowHearts.text = player.heartCount.toString()
            setAssigned(row.buttonRowHearts, player.heartCount > 0, R.color.ink_red)
            setAssigned(row.buttonRowQueen, player.qSpadesCount > 0, R.color.ink)
            setAssigned(row.buttonRowTen, player.tenDiamondsCount > 0, R.color.ink_red)
            // The Double belongs to whoever holds the whole round.
            setAssigned(row.buttonRowDouble, validity.isDouble && player.total == DOUBLE_TOTAL, null)

            row.buttonRowHearts.contentDescription =
                getString(R.string.cd_hearts_for, player.playerName)
            row.buttonRowQueen.contentDescription =
                getString(R.string.cd_queen_for, player.playerName)
            row.buttonRowTen.contentDescription =
                getString(R.string.cd_ten_for, player.playerName)
            row.buttonRowDouble.contentDescription =
                getString(R.string.cd_double_for, player.playerName)
        }
    }

    /**
     * An assigned button fills with brass and flips to ink, so a filled button
     * reads as a card actually sitting in front of that player.
     */
    private fun setAssigned(
        button: MaterialButton,
        assigned: Boolean,
        @ColorRes suitOnBrass: Int?
    ) {
        button.backgroundTintList =
            ColorStateList.valueOf(color(if (assigned) R.color.brass_400 else R.color.transparent))
        button.strokeColor =
            ColorStateList.valueOf(color(if (assigned) R.color.brass_600 else R.color.control_outline))
        button.setTextColor(color(if (assigned) R.color.felt_950 else R.color.text_primary))
        if (button.icon != null) {
            // Suits keep their printed colour on brass; on felt they would fall
            // below usable contrast, so they follow the label instead.
            val tint = if (assigned) suitOnBrass ?: R.color.felt_950 else R.color.text_secondary
            button.iconTint = ColorStateList.valueOf(color(tint))
        }
    }

    // ================================================================

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun View.showIf(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        heartPips.clear()
        _binding = null
    }
}

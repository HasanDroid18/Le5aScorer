package com.hasanDroid.le5ascorer.ui.round

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
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
import com.hasanDroid.le5ascorer.databinding.ViewCheckRowBinding
import com.hasanDroid.le5ascorer.databinding.ViewPlayerSeatBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val NO_ROUND = -1L
private const val REQUIRED_HEARTS = 13
private const val REQUIRED_TOTAL = 36
private const val DOUBLE_TOTAL = 37
private const val HEARTS_LONG_PRESS_STEP = 5

/**
 * Round entry, played on a table.
 *
 * Four seats sit around a felt table (positioned by the layout, using
 * ConstraintLayout circular positioning). Dealing is order-free: arm a card then
 * pick a seat, or pick a seat then arm a card — whichever you touch first is
 * remembered and the second completes the deal. Both stay set afterwards, so
 * dealing five hearts to one player is five taps rather than ten.
 *
 * The portrait and landscape layouts share every id and all three sub-layouts,
 * so nothing here branches on orientation.
 */
@AndroidEntryPoint
class RoundEntryFragment : Fragment() {

    private var _binding: FragmentRoundEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RoundEntryViewModel by viewModels()
    private val args: RoundEntryFragmentArgs by navArgs()

    /** Which card is waiting to be dealt, if any. */
    private enum class Card(@DrawableRes val icon: Int) {
        HEARTS(R.drawable.ic_suit_heart),
        QUEEN(R.drawable.ic_suit_spade),
        TEN(R.drawable.ic_suit_diamond),
        DOUBLE(R.drawable.ic_card_stack)
    }

    private var armed: Card? = null
    private var selectedSeat: Int? = null

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
        setupSeats()
        setupCardRail()
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
        binding.buttonReset.setOnClickListener {
            armed = null
            selectedSeat = null
            viewModel.reset()
        }
    }

    private fun seats(): List<ViewPlayerSeatBinding> =
        listOf(binding.seat1, binding.seat2, binding.seat3, binding.seat4)

    private fun setupSeats() {
        seats().forEachIndexed { index, seat ->
            seat.cardSeat.setOnClickListener { onSeatTapped(index) }
        }
    }

    private fun setupCardRail() {
        with(binding.cardRail) {
            cardHearts.setOnClickListener { onCardTapped(Card.HEARTS) }
            cardHearts.setOnLongClickListener {
                // +5 without five taps. Needs a seat, so arm one first if the
                // player has only picked the card so far.
                val seat = selectedSeat
                if (seat == null) {
                    onCardTapped(Card.HEARTS)
                } else {
                    repeat(HEARTS_LONG_PRESS_STEP) { viewModel.incrementHeart(seat) }
                }
                true
            }
            cardQSpades.setOnClickListener { onCardTapped(Card.QUEEN) }
            cardTenDiamonds.setOnClickListener { onCardTapped(Card.TEN) }
            cardDouble.setOnClickListener { onCardTapped(Card.DOUBLE) }
        }
        binding.buttonSave.setOnClickListener { viewModel.saveRound() }
    }

    // ================================================================
    // Dealing
    // ================================================================

    private fun onSeatTapped(seat: Int) {
        selectedSeat = seat
        armed?.let { deal(it, seat) }
        render(viewModel.uiState.value)
    }

    private fun onCardTapped(card: Card) {
        armed = card
        selectedSeat?.let { deal(card, it) }
        render(viewModel.uiState.value)
    }

    private fun deal(card: Card, seat: Int) {
        when (card) {
            Card.HEARTS -> viewModel.incrementHeart(seat)
            Card.QUEEN -> viewModel.toggleQSpades(seat)
            Card.TEN -> viewModel.toggleTenDiamonds(seat)
            Card.DOUBLE -> viewModel.setDouble(seat)
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
        updateSeats(state)
        updateTableCentre()
        updateCardRail()
        updateStatus(state, validity)
        updateActionChips(state)
        binding.buttonSave.isEnabled = !state.isSaving && validity.isValid
    }

    // ================================================================
    // Validity
    // ================================================================

    /**
     * The round's completeness, derived once per render. The same conditions
     * previously got recomputed independently in the status card and in the
     * save-button handler, which is how they drifted apart.
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

    private fun updateSeats(state: RoundEntryUiState) {
        val allSeats = seats()
        allSeats.forEachIndexed { index, seat ->
            val player = state.playersData.getOrNull(index)
            if (player == null) {
                seat.cardSeat.visibility = View.GONE
                return@forEachIndexed
            }
            seat.cardSeat.visibility = View.VISIBLE
            bindSeat(seat, player, isSelected = index == selectedSeat)
        }
    }

    private fun bindSeat(
        seat: ViewPlayerSeatBinding,
        player: PlayerRoundData,
        isSelected: Boolean
    ) {
        seat.textSeatName.text = player.playerName
        seat.textSeatScore.text = player.total.toString()

        seat.layoutHearts.showIf(player.heartCount > 0)
        seat.textHeartCount.text = getString(R.string.heart_count_format, player.heartCount)
        seat.iconQSpades.showIf(player.qSpadesCount > 0)
        seat.iconTenDiamonds.showIf(player.tenDiamondsCount > 0)

        seat.cardSeat.contentDescription = if (armed != null) {
            getString(R.string.cd_deal_to, player.playerName)
        } else {
            getString(R.string.cd_select_player, player.playerName)
        }

        // Stroke and elevation come from dimen resources: MaterialCardView takes
        // pixels, so raw literals rendered thinner on high-density screens.
        val res = resources
        if (isSelected) {
            seat.cardSeat.setCardBackgroundColor(color(R.color.brass_400))
            seat.cardSeat.strokeColor = color(R.color.brass_600)
            seat.cardSeat.strokeWidth = res.getDimensionPixelSize(R.dimen.stroke_selected)
            seat.cardSeat.cardElevation = res.getDimension(R.dimen.elev_float)
        } else {
            seat.cardSeat.setCardBackgroundColor(color(R.color.felt_700))
            seat.cardSeat.strokeColor = color(R.color.felt_outline)
            seat.cardSeat.strokeWidth = res.getDimensionPixelSize(R.dimen.stroke_hairline)
            seat.cardSeat.cardElevation = res.getDimension(R.dimen.elev_raised)
        }

        // On brass the seat reads as a printed card, so the suits take their
        // printed colours. On felt, printed red falls below usable contrast, so
        // the indicators go monochrome — the suit shapes still carry meaning.
        val onSeat = if (isSelected) R.color.felt_950 else R.color.text_primary
        val suitRed = if (isSelected) R.color.ink_red else R.color.text_secondary
        val suitBlack = if (isSelected) R.color.ink else R.color.text_secondary

        seat.textSeatName.setTextColor(color(onSeat))
        seat.textSeatScore.setTextColor(color(onSeat))
        seat.iconHearts.tint(suitRed)
        seat.textHeartCount.setTextColor(color(suitRed))
        seat.iconQSpades.tint(suitBlack)
        seat.iconTenDiamonds.tint(suitRed)
    }

    /** The middle of the table shows what is about to be dealt. */
    private fun updateTableCentre() {
        val card = armed
        binding.textArmedLabel.visibility = if (card == null) View.INVISIBLE else View.VISIBLE
        binding.imageArmed.visibility = if (card == null) View.INVISIBLE else View.VISIBLE
        binding.textTapHint.showIf(card == null && selectedSeat == null)
        card?.let { binding.imageArmed.setImageResource(it.icon) }
    }

    /** The armed card lifts out of the rail. */
    private fun updateCardRail() {
        val cards = with(binding.cardRail) {
            mapOf(
                Card.HEARTS to cardHearts,
                Card.QUEEN to cardQSpades,
                Card.TEN to cardTenDiamonds,
                Card.DOUBLE to cardDouble
            )
        }
        val res = resources
        cards.forEach { (card, view) ->
            val isArmed = card == armed
            view.cardElevation =
                res.getDimension(if (isArmed) R.dimen.elev_dialog else R.dimen.elev_float)
            view.strokeWidth =
                res.getDimensionPixelSize(if (isArmed) R.dimen.stroke_selected else R.dimen.stroke_none)
            view.strokeColor = color(R.color.brass_400)
            view.translationY =
                if (isArmed) -res.getDimension(R.dimen.space_sm) else 0f
        }
        // The Double keeps its own red edge when it is not the armed card.
        if (armed != Card.DOUBLE) {
            binding.cardRail.cardDouble.strokeWidth =
                res.getDimensionPixelSize(R.dimen.stroke_hairline)
            binding.cardRail.cardDouble.strokeColor = color(R.color.danger)
        }
    }

    private fun updateStatus(state: RoundEntryUiState, validity: Validity) {
        with(binding.roundStatus) {
            textRoundTotal.text =
                getString(R.string.round_total_format, validity.totalRound, validity.target)
            progressRound.max = validity.target
            progressRound.progress = validity.totalRound

            layoutDoubleStatus.showIf(validity.isDouble)
            layoutNormalStatus.showIf(!validity.isDouble)

            val accent = when {
                validity.isDouble -> R.color.danger
                validity.isValid -> R.color.success
                else -> R.color.brass_400
            }
            progressRound.setIndicatorColor(color(accent))
            textRoundTotal.setTextColor(color(accent))

            if (validity.isDouble) return

            bindCheck(
                checkHearts,
                met = validity.heartsComplete,
                label = getString(R.string.check_hearts, validity.totalHearts, REQUIRED_HEARTS)
            )
            bindCheck(
                checkQueen,
                met = validity.hasQueen,
                label = ownerLabel(
                    state, validity.qOwnerIndex, R.string.check_queen_to, R.string.check_queen
                )
            )
            bindCheck(
                checkTen,
                met = validity.hasTen,
                label = ownerLabel(
                    state, validity.tenOwnerIndex, R.string.check_ten_to, R.string.check_ten
                )
            )
        }
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

    private fun addActionChip(label: String, @DrawableRes iconRes: Int, onRemove: () -> Unit) {
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

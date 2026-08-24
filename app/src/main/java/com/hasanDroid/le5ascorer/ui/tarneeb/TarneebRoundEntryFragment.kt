package com.hasanDroid.le5ascorer.ui.tarneeb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentTarneebRoundEntryBinding
import com.hasanDroid.le5ascorer.databinding.ViewTarneebTeamRowBinding
import com.hasanDroid.le5ascorer.domain.TarneebScoreEngine
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import com.hasanDroid.le5ascorer.ui.common.applySystemBarMargins
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val NO_ROUND = -1L

/**
 * Tarneeb round entry: who bid, how much, and how the tricks fell.
 *
 * The thirteen tricks are a fixed pool, so the steppers cannot over-assign and
 * the counter says how many are still unplaced. Save stays disabled until all
 * thirteen have a home, and the card above it shows what the round will award
 * before it is committed.
 */
@AndroidEntryPoint
class TarneebRoundEntryFragment : Fragment() {

    private var _binding: FragmentTarneebRoundEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TarneebRoundEntryViewModel by viewModels()
    private val args: TarneebRoundEntryFragmentArgs by navArgs()

    private var navigatedAway = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTarneebRoundEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initialize(
            args.matchId,
            args.teamNames.toList(),
            if (args.roundId == NO_ROUND) null else args.roundId
        )

        applyInsets()
        setupToolbar()
        setupControls()
        observe()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)
        // Margins rather than padding, so the button moves clear of the gesture
        // bar instead of growing underneath it.
        binding.buttonSave.applySystemBarMargins(bottom = true, sides = false)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        // Nothing else on this screen distinguished editing an existing round
        // from adding a new one, which matters because Reset blanks whichever
        // you are on. Leekha's round entry already titles itself this way.
        binding.toolbar.setTitle(
            if (args.roundId == NO_ROUND) R.string.tarneeb_round_title else R.string.edit_round
        )
        binding.buttonReset.setOnClickListener { viewModel.reset() }
    }

    private fun rows(): List<ViewTarneebTeamRowBinding> =
        listOf(binding.rowTeamA, binding.rowTeamB)

    private fun setupControls() {
        binding.toggleBidder.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.setBidder(if (checkedId == R.id.buttonBidderB) 1 else 0)
        }
        binding.buttonBidDown.setOnClickListener { viewModel.changeBid(-1) }
        binding.buttonBidUp.setOnClickListener { viewModel.changeBid(+1) }

        rows().forEachIndexed { team, row ->
            row.buttonTricksDown.setOnClickListener { viewModel.changeTricks(team, -1) }
            row.buttonTricksUp.setOnClickListener { viewModel.changeTricks(team, +1) }
            // The last few tricks are almost always all one team's; long-press
            // sweeps the remainder rather than making you tap it out.
            row.buttonTricksUp.setOnLongClickListener {
                viewModel.assignRemainingTo(team)
                true
            }
        }

        binding.buttonSave.setOnClickListener { viewModel.save() }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.saved) {
                        // One-shot. `saved` is never cleared, and render() can
                        // provoke a further emission of its own (checking the
                        // bidder toggle calls back into the ViewModel), so a
                        // second navigateUp would pop the scoreboard too and
                        // strand the user on the match list. Returning here also
                        // keeps render() off a binding that is about to go away.
                        if (!navigatedAway) {
                            navigatedAway = true
                            findNavController().navigateUp()
                        }
                        return@collect
                    }
                    render(state)
                }
            }
        }
    }

    private fun render(state: TarneebRoundUiState) {
        binding.buttonBidderA.text = state.teamNames.getOrNull(0).orEmpty()
        binding.buttonBidderB.text = state.teamNames.getOrNull(1).orEmpty()
        binding.buttonBidderA.contentDescription =
            getString(R.string.cd_tarneeb_select_team, binding.buttonBidderA.text)
        binding.buttonBidderB.contentDescription =
            getString(R.string.cd_tarneeb_select_team, binding.buttonBidderB.text)

        val expected = if (state.bidderTeam == 1) R.id.buttonBidderB else R.id.buttonBidderA
        if (binding.toggleBidder.checkedButtonId != expected) {
            binding.toggleBidder.check(expected)
        }

        binding.textBid.text = state.bid.toString()
        binding.textBidCaption.text = if (state.bid == TarneebScoreEngine.TRICKS_PER_ROUND) {
            getString(R.string.tarneeb_kabbout)
        } else {
            // The traditional name: bid 1 is seven tricks, bid 7 is all thirteen.
            getString(R.string.tarneeb_bid_lamas, state.bid - TarneebScoreEngine.MIN_BID + 1)
        }
        binding.buttonBidDown.isEnabled =
            !state.isLoading && state.bid > TarneebScoreEngine.MIN_BID
        binding.buttonBidUp.isEnabled =
            !state.isLoading && state.bid < TarneebScoreEngine.TRICKS_PER_ROUND

        renderRows(state)
        renderRemaining(state)
        renderPreview(state)

        binding.buttonSave.isEnabled = state.isComplete && !state.isSaving && !state.isLoading
    }

    private fun renderRows(state: TarneebRoundUiState) {
        rows().forEachIndexed { team, row ->
            val name = state.teamNames.getOrNull(team).orEmpty()
            row.textTeamName.text = name
            row.textTricks.text = state.tricks.getOrElse(team) { 0 }.toString()

            val isBidder = state.bidderTeam == team
            row.textTeamBid.visibility = if (isBidder) View.VISIBLE else View.GONE
            if (isBidder) {
                row.textTeamBid.text = getString(R.string.tarneeb_bidder_marker, state.bid)
            }

            row.buttonTricksDown.isEnabled =
                !state.isLoading && state.tricks.getOrElse(team) { 0 } > 0
            row.buttonTricksUp.isEnabled = !state.isLoading && viewModel.canAddTrick(team)
            row.buttonTricksDown.contentDescription =
                getString(R.string.cd_tarneeb_tricks_down, name)
            row.buttonTricksUp.contentDescription =
                getString(R.string.cd_tarneeb_tricks_up, name)
        }
    }

    private fun renderRemaining(state: TarneebRoundUiState) {
        binding.textTricksRemaining.text = if (state.isComplete) {
            getString(
                R.string.tarneeb_tricks_format,
                TarneebScoreEngine.TRICKS_PER_ROUND,
                TarneebScoreEngine.TRICKS_PER_ROUND
            )
        } else {
            getString(R.string.tarneeb_tricks_remaining, state.remaining)
        }
        binding.textTricksRemaining.setTextColor(
            color(if (state.isComplete) R.color.success else R.color.text_secondary)
        )
    }

    /** What the round will pay, so a wrong entry is visible before it is saved. */
    private fun renderPreview(state: TarneebRoundUiState) {
        binding.cardPreview.visibility = if (state.isComplete) View.VISIBLE else View.GONE
        if (!state.isComplete) return

        val points = viewModel.previewPoints()
        binding.textPreviewA.applyPoints(points.getOrElse(0) { 0 })
        binding.textPreviewB.applyPoints(points.getOrElse(1) { 0 })

        val bidderTricks = state.tricks.getOrElse(state.bidderTeam) { 0 }
        binding.textPreviewVerdict.setText(
            when {
                bidderTricks == TarneebScoreEngine.TRICKS_PER_ROUND -> R.string.tarneeb_preview_sweep
                bidderTricks >= state.bid -> R.string.tarneeb_preview_made
                else -> R.string.tarneeb_preview_failed
            }
        )
    }

    /** Signed and coloured: a loss should never read as a gain at a glance. */
    private fun TextView.applyPoints(points: Int) {
        text = when {
            points > 0 -> getString(R.string.tarneeb_points_positive, points)
            points < 0 -> getString(R.string.tarneeb_points_negative, -points)
            else -> getString(R.string.tarneeb_points_zero)
        }
        setTextColor(
            color(
                when {
                    points > 0 -> R.color.success
                    points < 0 -> R.color.danger_bright
                    else -> R.color.text_tertiary
                }
            )
        )
    }

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

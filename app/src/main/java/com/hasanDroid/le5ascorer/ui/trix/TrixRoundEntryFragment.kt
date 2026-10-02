package com.hasanDroid.le5ascorer.ui.trix

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.button.MaterialButtonToggleGroup
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentTrixRoundEntryBinding
import com.hasanDroid.le5ascorer.databinding.ViewTarneebTeamRowBinding
import com.hasanDroid.le5ascorer.databinding.ViewTrixPlaceRowBinding
import com.hasanDroid.le5ascorer.databinding.ViewTrixQueenRowBinding
import com.hasanDroid.le5ascorer.domain.TrixContractType
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import com.hasanDroid.le5ascorer.ui.common.applySystemBarMargins
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val NO_ROUND = -1L

/**
 * Entry for one Trix contract. The scoreboard says which contract; this screen
 * shows only that contract's questions, and Confirm stays disabled until every
 * one is answered.
 */
@AndroidEntryPoint
class TrixRoundEntryFragment : Fragment() {

    private var _binding: FragmentTrixRoundEntryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrixRoundEntryViewModel by viewModels()
    private val args: TrixRoundEntryFragmentArgs by navArgs()

    private var navigatedAway = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrixRoundEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val type = TrixContractType.valueOf(args.contractType)
        viewModel.initialize(
            matchId = args.matchId,
            type = type,
            playerNames = args.playerNames.toList(),
            doubling = args.doubling,
            roundId = args.roundId.takeIf { it != NO_ROUND }
        )

        applyInsets()
        binding.toolbar.setTitle(type.titleRes)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        showPanel(type)
        setupKing()
        setupQueens()
        setupCounts()
        setupTrix()
        binding.buttonConfirm.setOnClickListener { viewModel.save() }
        observe()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)
        // Margins rather than padding, so the button moves clear of the gesture
        // bar instead of growing underneath it.
        binding.buttonConfirm.applySystemBarMargins(bottom = true, sides = false)
    }

    private fun showPanel(type: TrixContractType) {
        binding.panelKing.isVisible = type == TrixContractType.KING
        binding.panelQueens.isVisible = type == TrixContractType.QUEENS
        binding.panelCounts.isVisible =
            type == TrixContractType.DIAMONDS || type == TrixContractType.LTOOSH
        binding.panelTrix.isVisible = type == TrixContractType.TRIX
        binding.textCountsLabel.setText(
            if (type == TrixContractType.DIAMONDS) R.string.trix_diamonds_taken else R.string.trix_tricks_taken
        )
    }

    private fun queenRows(): List<ViewTrixQueenRowBinding> = listOf(
        binding.rowQueenSpades, binding.rowQueenHearts, binding.rowQueenDiamonds, binding.rowQueenClubs
    )

    private fun countRows(): List<ViewTarneebTeamRowBinding> = listOf(binding.rowCountA, binding.rowCountB)

    private fun placeRows(): List<ViewTrixPlaceRowBinding> =
        listOf(binding.rowPlace0, binding.rowPlace1, binding.rowPlace2, binding.rowPlace3)

    private fun setupKing() {
        binding.toggleKingTeam.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.setKingTeam(if (id == R.id.buttonKingTeamB) 1 else 0)
        }
        binding.toggleKingDoubled.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.setKingDoubled(id == R.id.buttonKingDoubledYes)
        }
    }

    private fun setupQueens() {
        val suits = listOf(
            R.string.trix_queen_spades, R.string.trix_queen_hearts,
            R.string.trix_queen_diamonds, R.string.trix_queen_clubs
        )
        queenRows().forEachIndexed { suit, row ->
            row.textQueenSuit.setText(suits[suit])
            // ♥ and ♦ are the red suits.
            if (suit == 1 || suit == 2) {
                row.textQueenSuit.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger_bright))
            }
            row.toggleQueenTeam.addOnButtonCheckedListener { _, id, checked ->
                if (checked) viewModel.setQueenTeam(suit, if (id == R.id.buttonQueenTeamB) 1 else 0)
            }
            row.toggleQueenDoubled.addOnButtonCheckedListener { _, id, checked ->
                if (checked) viewModel.setQueenDoubled(suit, id == R.id.buttonQueenDoubledYes)
            }
        }
    }

    private fun setupCounts() {
        countRows().forEachIndexed { team, row ->
            row.textTeamBid.isVisible = false
            row.buttonTricksDown.setOnClickListener { viewModel.changeCount(team, -1) }
            row.buttonTricksUp.setOnClickListener { viewModel.changeCount(team, +1) }
        }
    }

    private fun setupTrix() {
        val placeIds = listOf(R.id.buttonPlace1, R.id.buttonPlace2, R.id.buttonPlace3, R.id.buttonPlace4)
        placeRows().forEachIndexed { seat, row ->
            row.togglePlace.addOnButtonCheckedListener { _, id, checked ->
                if (checked) viewModel.setPlace(seat, placeIds.indexOf(id) + 1)
            }
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.saved) {
                        // One-shot, for the reason TarneebRoundEntryFragment gives:
                        // a second navigateUp would pop the scoreboard too.
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

    private fun render(state: TrixRoundUiState) {
        val teams = state.teamNames
        // Enable before selecting: a disabled MaterialButton ignores setChecked,
        // so checking while still disabled from loading would leave a stored
        // answer unshown when editing.
        val enabled = !state.isLoading
        binding.toggleKingTeam.isEnabled = enabled
        binding.toggleKingDoubled.isEnabled = enabled

        binding.buttonKingTeamA.text = teams.getOrNull(0).orEmpty()
        binding.buttonKingTeamB.text = teams.getOrNull(1).orEmpty()
        binding.toggleKingTeam.select(state.kingTeam?.let { if (it == 1) R.id.buttonKingTeamB else R.id.buttonKingTeamA })
        binding.layoutKingDoubled.isVisible = state.doubling
        binding.toggleKingDoubled.select(if (state.kingDoubled) R.id.buttonKingDoubledYes else R.id.buttonKingDoubledNo)

        queenRows().forEachIndexed { suit, row ->
            row.toggleQueenTeam.isEnabled = enabled
            row.toggleQueenDoubled.isEnabled = enabled
            row.buttonQueenTeamA.text = teams.getOrNull(0).orEmpty()
            row.buttonQueenTeamB.text = teams.getOrNull(1).orEmpty()
            row.toggleQueenTeam.select(
                state.queenTeams[suit]?.let { if (it == 1) R.id.buttonQueenTeamB else R.id.buttonQueenTeamA }
            )
            row.layoutQueenDoubled.isVisible = state.doubling
            row.toggleQueenDoubled.select(
                if (state.queenDoubled[suit]) R.id.buttonQueenDoubledYes else R.id.buttonQueenDoubledNo
            )
        }

        val counted = state.counts.sum()
        binding.textCountBadge.text =
            getString(R.string.trix_count_format, counted, TrixScoreEngine.HAND_SIZE)
        binding.textCountBadge.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (counted == TrixScoreEngine.HAND_SIZE) R.color.success else R.color.text_secondary
            )
        )
        countRows().forEachIndexed { team, row ->
            val name = teams.getOrNull(team).orEmpty()
            row.textTeamName.text = name
            row.textTricks.text = state.counts[team].toString()
            row.buttonTricksDown.isEnabled = enabled && state.counts[team] > 0
            row.buttonTricksUp.isEnabled = enabled && state.counts[team] < TrixScoreEngine.HAND_SIZE
            row.buttonTricksDown.contentDescription = getString(R.string.cd_trix_count_down, name)
            row.buttonTricksUp.contentDescription = getString(R.string.cd_trix_count_up, name)
        }

        val placeIds = listOf(R.id.buttonPlace1, R.id.buttonPlace2, R.id.buttonPlace3, R.id.buttonPlace4)
        placeRows().forEachIndexed { seat, row ->
            row.textPlaceName.text = state.playerNames.getOrNull(seat).orEmpty()
            val place = state.places[seat]
            row.togglePlace.isEnabled = enabled
            row.togglePlace.select(place?.let { placeIds[it - 1] })
            if (place == null) {
                row.textPlacePoints.text = ""
            } else {
                row.textPlacePoints.showPoints(TrixScoreEngine.TRIX_POINTS[place - 1])
            }
        }

        val preview = viewModel.previewPoints()
        binding.cardPreview.isVisible = preview != null
        if (preview != null) {
            binding.textPreviewA.showPoints(preview[0])
            binding.textPreviewB.showPoints(preview[1])
        }

        binding.buttonConfirm.isEnabled = preview != null && !state.isSaving && enabled
    }

    /** Matches the group to the state without re-checking what is already checked. */
    private fun MaterialButtonToggleGroup.select(id: Int?) {
        when {
            id == null -> if (checkedButtonId != View.NO_ID) clearChecked()
            checkedButtonId != id -> check(id)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

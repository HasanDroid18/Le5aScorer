package com.hasanDroid.le5ascorer.ui.trix

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.databinding.FragmentTrixScoreboardBinding
import com.hasanDroid.le5ascorer.databinding.ViewStandingColumnBinding
import com.hasanDroid.le5ascorer.databinding.ViewTrixContractRowBinding
import com.hasanDroid.le5ascorer.domain.TrixContractType
import com.hasanDroid.le5ascorer.domain.TrixScoreEngine
import com.hasanDroid.le5ascorer.domain.TrixScoreboard
import com.hasanDroid.le5ascorer.ui.common.EndGameDialogFragment
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val NEW_ROUND = -1L
private const val PLAYED_ALPHA = 0.4f

/**
 * Trix kingdom screen: team totals, whose kingdom it is, and which of its five
 * contracts are still to play. The owner picks one, Enter Round opens its entry.
 */
@AndroidEntryPoint
class TrixScoreboardFragment : Fragment() {

    private var _binding: FragmentTrixScoreboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrixScoreboardViewModel by viewModels()
    private val args: TrixScoreboardFragmentArgs by navArgs()

    private lateinit var historyAdapter: TrixHistoryAdapter
    private var historyOpen = false

    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrixScoreboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.layoutButtons.applySystemBarInsets(bottom = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupHistory()
        setupContracts()
        setupButtons()

        viewModel.load(args.matchId)
        observe()
    }

    private fun contractRows(): Map<TrixContractType, ViewTrixContractRowBinding> = mapOf(
        TrixContractType.KING to binding.rowKing,
        TrixContractType.QUEENS to binding.rowQueens,
        TrixContractType.DIAMONDS to binding.rowDiamonds,
        TrixContractType.LTOOSH to binding.rowLtoosh,
        TrixContractType.TRIX to binding.rowTrix
    )

    private fun setupHistory() {
        historyAdapter = TrixHistoryAdapter { result ->
            openRoundEntry(result.contract.type, result.roundId)
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = historyAdapter
        binding.buttonHistory.setOnClickListener {
            historyOpen = !historyOpen
            binding.recyclerHistory.isVisible = historyOpen
            binding.buttonHistory.rotation = if (historyOpen) 270f else 90f
        }
    }

    private fun setupContracts() {
        contractRows().forEach { (type, row) ->
            row.iconContract.setImageResource(type.iconRes)
            row.iconContract.imageTintList = ColorStateList.valueOf(color(type.iconTint))
            row.textContractTitle.setText(type.titleRes)
            row.root.setOnClickListener { viewModel.select(type) }
        }
    }

    private fun setupButtons() {
        binding.buttonEnterRound.setOnClickListener {
            viewModel.uiState.value.selected?.let { openRoundEntry(it, NEW_ROUND) }
        }
        binding.buttonBackToHome.setOnClickListener {
            // Must carry the game mode, or the list falls back to Leekha.
            findNavController().navigate(
                TrixScoreboardFragmentDirections
                    .actionTrixScoreboardFragmentToMatchListFragment(ScoreRule.TRIX.name)
            )
        }
    }

    private fun openRoundEntry(type: TrixContractType, roundId: Long) {
        val state = viewModel.uiState.value
        if (state.playerNames.size < TrixScoreEngine.SEATS) return
        findNavController().navigate(
            TrixScoreboardFragmentDirections.actionTrixScoreboardFragmentToTrixRoundEntryFragment(
                matchId = args.matchId,
                contractType = type.name,
                playerNames = state.playerNames.toTypedArray(),
                doubling = state.setup.doubling,
                roundId = roundId
            )
        )
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: TrixScoreboardUiState) {
        val board = state.board ?: return
        val over = board.isGameOver

        binding.toolbar.subtitle =
            if (state.setup.doubling) getString(R.string.trix_doubling_on) else null

        bindColumn(binding.standingTeamA, state.teamNames.getOrNull(0).orEmpty(), board.totals[0])
        bindColumn(binding.standingTeamB, state.teamNames.getOrNull(1).orEmpty(), board.totals[1])

        historyAdapter.submitData(board.results, state.playerNames)
        binding.buttonHistory.isVisible = board.results.isNotEmpty()
        binding.recyclerHistory.isVisible = historyOpen && board.results.isNotEmpty()

        binding.cardResult.isVisible = over
        if (over) {
            binding.textResult.text = board.winnerTeam
                ?.let { getString(R.string.trix_team_wins, state.teamNames.getOrNull(it).orEmpty()) }
                ?: getString(R.string.trix_draw)
        }

        binding.layoutKingdom.isVisible = !over
        binding.layoutContracts.isVisible = !over
        binding.textKingdomOwner.text = getString(
            R.string.trix_kingdom_owner,
            state.playerNames.getOrNull(board.ownerSeat).orEmpty()
        )
        binding.textKingdomCount.text =
            getString(R.string.trix_kingdom_count, board.kingdom + 1, TrixScoreEngine.KINGDOMS)

        contractRows().forEach { (type, row) ->
            bindContractRow(
                row = row,
                type = type,
                played = type in board.playedInKingdom,
                selected = state.selected == type,
                doubling = state.setup.doubling
            )
        }

        binding.buttonEnterRound.isVisible = !over
        binding.buttonEnterRound.isEnabled = state.selected != null
        binding.buttonBackToHome.isVisible = over

        maybeRoastTheLosers(board, state.teamNames)
    }

    /** A team's total; red once it goes below zero, as on the reference design. */
    private fun bindColumn(column: ViewStandingColumnBinding, name: String, total: Int) {
        column.textColumnName.text = name
        column.textColumnTotal.text = total.toString()
        column.textColumnTotal.setTextColor(
            color(if (total < 0) R.color.danger_bright else R.color.text_primary)
        )
        // Trix has no target to travel toward.
        column.progressColumn.isVisible = false
        column.root.contentDescription = getString(R.string.cd_trix_standing, name, total)
    }

    private fun bindContractRow(
        row: ViewTrixContractRowBinding,
        type: TrixContractType,
        played: Boolean,
        selected: Boolean,
        doubling: Boolean
    ) {
        row.textContractRule.setText(type.ruleRes(doubling))
        row.root.isEnabled = !played
        row.root.alpha = if (played) PLAYED_ALPHA else 1f
        row.root.strokeColor = color(if (selected) R.color.success else R.color.felt_outline)
        row.root.strokeWidth = resources.getDimensionPixelSize(
            if (selected) R.dimen.stroke_selected else R.dimen.stroke_hairline
        )
        row.dotSelected.isVisible = selected
    }

    /**
     * The same send-off Tarneeb gives, pointed at the losing team. Keyed on who
     * lost, so an edit that changes the outcome announces the new result and a
     * re-render of the same one does not. A draw has no loser and no dialog.
     */
    private fun maybeRoastTheLosers(board: TrixScoreboard, teamNames: List<String>) {
        val key = LAST_LOSER_KEY + args.matchId
        val winner = board.winnerTeam
        if (!board.isGameOver || winner == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val loserName = teamNames.getOrNull(1 - winner) ?: return
        if (prefs.getString(key, null) == loserName) return

        prefs.edit().putString(key, loserName).apply()
        EndGameDialogFragment.newInstance(listOf(loserName), args.matchId).apply {
            onShowRoundScores = { binding.scrollView.smoothScrollTo(0, 0) }
            onPhotoSaved = { path ->
                viewModel.saveLoserImage(path)
                binding.scrollView.smoothScrollTo(0, 0)
            }
        }.show(childFragmentManager, "TrixEndGameDialog")
    }

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val PREFS_NAME = "le5a_scorer_prefs"
        const val LAST_LOSER_KEY = "trix_last_loser_"
    }
}

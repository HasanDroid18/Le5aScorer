package com.hasanDroid.le5ascorer.ui.tarneeb

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
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
import com.hasanDroid.le5ascorer.databinding.FragmentTarneebScoreboardBinding
import com.hasanDroid.le5ascorer.databinding.ViewStandingColumnBinding
import com.hasanDroid.le5ascorer.domain.TarneebGameResult
import com.hasanDroid.le5ascorer.ui.common.EndGameDialogFragment
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val NEW_ROUND = -1L

/**
 * Tarneeb scoreboard.
 *
 * Separate from the Leekha scoreboard because the two disagree about what the
 * target means: reaching it wins here and loses there. That inverts the banner,
 * the accent colours and the progress bars, which is more than a flag's worth
 * of difference.
 */
@AndroidEntryPoint
class TarneebScoreboardFragment : Fragment() {

    private var _binding: FragmentTarneebScoreboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TarneebScoreboardViewModel by viewModels()
    private val args: TarneebScoreboardFragmentArgs by navArgs()

    private lateinit var adapter: TarneebRoundAdapter

    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTarneebScoreboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupToolbar()
        setupEmptyState()
        setupRecyclerView()
        setupButtons()

        viewModel.load(args.matchId)
        observe()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        // The container takes the inset, not the buttons inside it.
        binding.layoutButtons.applySystemBarInsets(bottom = true)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupEmptyState() {
        binding.emptyState.imageEmpty.setImageResource(R.drawable.ic_cards_empty)
        binding.emptyState.textEmptyTitle.setText(R.string.tarneeb_no_rounds)
        binding.emptyState.textEmptyBody.setText(R.string.tarneeb_no_rounds_hint)
    }

    private fun setupRecyclerView() {
        adapter = TarneebRoundAdapter(onEditRound = { roundId -> openRoundEntry(roundId) })
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TarneebScoreboardFragment.adapter
        }
    }

    private fun setupButtons() {
        binding.buttonAddRound.setOnClickListener { openRoundEntry(NEW_ROUND) }
        binding.buttonBackToHome.setOnClickListener {
            // Must carry the game mode. Navigating by bare action id would use
            // the argument's default and drop the user on the *Leekha* list.
            findNavController().navigate(
                TarneebScoreboardFragmentDirections
                    .actionTarneebScoreboardFragmentToMatchListFragment(ScoreRule.TARNEEB.name)
            )
        }
    }

    private fun openRoundEntry(roundId: Long) {
        val names = viewModel.uiState.value.teamNames
        if (names.isEmpty()) return
        findNavController().navigate(
            TarneebScoreboardFragmentDirections
                .actionTarneebScoreboardFragmentToTarneebRoundEntryFragment(
                    matchId = args.matchId,
                    teamNames = names.toTypedArray(),
                    roundId = roundId
                )
        )
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: TarneebScoreboardUiState) {
        val detail = state.matchDetail ?: return

        bindStandings(state)

        // The adapter needs round ids to route edits; the engine works in round
        // indices, so map between them here rather than widening the result type.
        adapter.submitData(
            results = state.results,
            teamNames = state.teamNames,
            roundIds = detail.rounds.associate { it.roundIndex to it.id }
        )

        val hasRounds = state.results.isNotEmpty()
        binding.recyclerView.visibility = if (hasRounds) View.VISIBLE else View.GONE
        binding.emptyState.root.visibility = if (hasRounds) View.GONE else View.VISIBLE

        val result = state.gameResult
        binding.cardResult.visibility = if (result != null) View.VISIBLE else View.GONE
        if (result != null) {
            binding.textResult.text = getString(R.string.tarneeb_match_won, result.winnerName)
        }
        binding.buttonAddRound.visibility = if (result == null) View.VISIBLE else View.GONE
        binding.buttonBackToHome.visibility = if (result == null) View.GONE else View.VISIBLE

        maybeRoastTheLosers(result)
    }

    /**
     * The same send-off Leekha gives, pointed at the team that did not win.
     *
     * Keyed on who lost rather than on a "shown once" flag, so editing a round
     * that changes the outcome announces the new result — and re-showing the
     * same one does not.
     */
    private fun maybeRoastTheLosers(result: TarneebGameResult?) {
        val key = LAST_LOSER_KEY + args.matchId
        if (result == null) {
            prefs.edit().remove(key).apply()
            return
        }
        if (prefs.getString(key, null) == result.loserName) return

        prefs.edit().putString(key, result.loserName).apply()
        EndGameDialogFragment.newInstance(listOf(result.loserName), args.matchId).apply {
            onShowRoundScores = { binding.scrollView.smoothScrollTo(0, 0) }
            onPhotoSaved = { path ->
                viewModel.saveLoserImage(path)
                binding.scrollView.smoothScrollTo(0, 0)
            }
        }.show(childFragmentManager, "TarneebEndGameDialog")
    }

    /**
     * Both teams' totals and their progress toward the target.
     *
     * The accent is the opposite way round from Leekha: the higher total is the
     * team about to win, so it gets the positive colour.
     */
    private fun bindStandings(state: TarneebScoreboardUiState) {
        val detail = state.matchDetail ?: return
        val target = detail.match.terminalScore.coerceAtLeast(1)
        val totals = state.totals
        val columns = listOf(binding.standingTeamA, binding.standingTeamB)

        binding.textTarget.text =
            getString(R.string.scoreboard_target_format, detail.match.terminalScore)

        val most = totals.maxOrNull() ?: 0
        val fewest = totals.minOrNull() ?: 0

        columns.forEachIndexed { team, column ->
            bindColumn(
                column = column,
                name = state.teamNames.getOrNull(team).orEmpty(),
                total = totals.getOrElse(team) { 0 },
                target = target,
                displayTarget = detail.match.terminalScore,
                level = most == fewest,
                isAhead = totals.getOrElse(team) { 0 } == most
            )
        }
    }

    private fun bindColumn(
        column: ViewStandingColumnBinding,
        name: String,
        total: Int,
        target: Int,
        displayTarget: Int,
        level: Boolean,
        isAhead: Boolean
    ) {
        val colorRes = when {
            level -> R.color.text_primary
            isAhead -> R.color.success
            else -> R.color.danger_bright
        }
        val tint = color(colorRes)

        column.textColumnName.text = name
        column.textColumnTotal.text = total.toString()
        column.textColumnTotal.setTextColor(tint)
        column.progressColumn.setIndicatorColor(tint)
        column.progressColumn.max = target
        // A failed bid can push a total below zero, and a negative progress
        // value throws.
        column.progressColumn.progress = total.coerceIn(0, target)
        column.root.contentDescription =
            getString(R.string.cd_tarneeb_standing, name, total, displayTarget)
    }

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    private companion object {
        const val PREFS_NAME = "le5a_scorer_prefs"
        const val LAST_LOSER_KEY = "tarneeb_last_loser_"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

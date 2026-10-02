package com.hasanDroid.le5ascorer.ui.matches

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.databinding.FragmentMatchListBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import com.hasanDroid.le5ascorer.ui.common.applySystemBarMargins
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * The match list for one game.
 *
 * Serves Leekha, Tarneeb and Trix. The lists differ only in which matches
 * they show, what the toolbar says, and where the add button goes, so they share
 * a screen rather than a copy of one — the gameMode argument decides.
 */
@AndroidEntryPoint
class MatchListFragment : Fragment() {

    private var _binding: FragmentMatchListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MatchListViewModel by viewModels()
    private val args: MatchListFragmentArgs by navArgs()

    /**
     * Unknown values fall back to Leekha rather than crashing, matching how
     * Converters treats the same column.
     */
    private val gameMode: ScoreRule by lazy {
        runCatching { ScoreRule.valueOf(args.gameMode) }.getOrDefault(ScoreRule.INDIVIDUAL)
    }

    private lateinit var inProgressAdapter: MatchAdapter
    private lateinit var completedAdapter: MatchAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.start(gameMode)

        applyInsets()
        setupToolbar()
        setupTabs()
        setupEmptyStates()
        setupRecyclerViews()
        setupFab()
        observeUiState()
    }

    /** App bar takes the status bar; lists and FAB take the navigation bar. */
    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.recyclerViewInProgress.applySystemBarInsets(bottom = true)
        binding.recyclerViewCompleted.applySystemBarInsets(bottom = true)
        binding.fab.applySystemBarMargins(bottom = true, sides = false)
    }

    private fun setupToolbar() {
        binding.toolbar.setTitle(
            when (gameMode) {
                ScoreRule.TARNEEB -> R.string.game_tarneeb
                ScoreRule.TRIX -> R.string.game_trix
                ScoreRule.INDIVIDUAL -> R.string.game_leekha
            }
        )
        // This screen used to be the start destination and had nowhere to go
        // back to. It sits behind the game picker now, so it needs the arrow.
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    findNavController().navigate(
                        MatchListFragmentDirections.actionMatchListFragmentToSettingsFragment()
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.in_progress))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.completed))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val showInProgress = tab?.position == 0
                binding.layoutInProgress.visibility =
                    if (showInProgress) View.VISIBLE else View.GONE
                binding.layoutCompleted.visibility =
                    if (showInProgress) View.GONE else View.VISIBLE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun setupEmptyStates() {
        with(binding.emptyStateInProgress) {
            imageEmpty.setImageResource(R.drawable.ic_cards_empty)
            textEmptyTitle.setText(R.string.no_games_in_progress)
            textEmptyBody.setText(R.string.no_games_in_progress_hint)
        }
        with(binding.emptyStateCompleted) {
            imageEmpty.setImageResource(R.drawable.ic_trophy)
            textEmptyTitle.setText(R.string.no_completed_games)
            textEmptyBody.setText(R.string.no_completed_games_hint)
        }
    }

    private fun setupRecyclerViews() {
        inProgressAdapter = buildAdapter()
        completedAdapter = buildAdapter()

        binding.recyclerViewInProgress.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = inProgressAdapter
        }
        binding.recyclerViewCompleted.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = completedAdapter
        }
    }

    private fun buildAdapter() = MatchAdapter(
        onMatchClick = { match ->
            val directions = when (gameMode) {
                ScoreRule.TARNEEB -> MatchListFragmentDirections
                    .actionMatchListFragmentToTarneebScoreboardFragment(match.id)
                ScoreRule.TRIX -> MatchListFragmentDirections
                    .actionMatchListFragmentToTrixScoreboardFragment(match.id)
                ScoreRule.INDIVIDUAL -> MatchListFragmentDirections
                    .actionMatchListFragmentToScoreboardFragment(match.id)
            }
            findNavController().navigate(directions)
        },
        onDuplicateClick = { match -> viewModel.duplicateMatch(match.id) },
        onDeleteClick = { match -> showDeleteConfirmation(match.id) }
    )

    private fun setupFab() {
        binding.fab.setOnClickListener {
            findNavController().navigate(
                when (gameMode) {
                    ScoreRule.TARNEEB -> R.id.action_matchListFragment_to_newTarneebMatchFragment
                    ScoreRule.TRIX -> R.id.action_matchListFragment_to_newTrixMatchFragment
                    ScoreRule.INDIVIDUAL -> R.id.action_matchListFragment_to_newMatchFragment
                }
            )
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    inProgressAdapter.submitList(state.inProgressMatches)
                    completedAdapter.submitList(state.completedMatches)

                    // Both tabs are updated regardless of which is on screen, so
                    // switching tabs never shows a stale empty state.
                    toggleEmptyState(
                        isEmpty = state.inProgressMatches.isEmpty(),
                        list = binding.recyclerViewInProgress,
                        empty = binding.emptyStateInProgress.root
                    )
                    toggleEmptyState(
                        isEmpty = state.completedMatches.isEmpty(),
                        list = binding.recyclerViewCompleted,
                        empty = binding.emptyStateCompleted.root
                    )
                }
            }
        }
    }

    private fun toggleEmptyState(isEmpty: Boolean, list: View, empty: View) {
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun showDeleteConfirmation(matchId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_match_title)
            .setMessage(R.string.delete_match_message)
            .setPositiveButton(R.string.delete_confirm) { _, _ -> viewModel.deleteMatch(matchId) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

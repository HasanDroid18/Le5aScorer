package com.hasandroid.le5ascorer.ui.matches

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hasandroid.le5ascorer.R
import com.hasandroid.le5ascorer.databinding.FragmentMatchListBinding
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MatchListFragment : Fragment() {

    private var _binding: FragmentMatchListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MatchListViewModel by viewModels()
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

        setupToolbar()
        setupTabs()
        setupRecyclerViews()
        setupFab()
        setupBackPressHandler()
        observeUiState()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Exit app when back pressed on home screen
            requireActivity().finish()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.app_name)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    val action = MatchListFragmentDirections.actionMatchListFragmentToSettingsFragment()
                    findNavController().navigate(action)
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
                when (tab?.position) {
                    0 -> {
                        binding.layoutInProgress.visibility = View.VISIBLE
                        binding.layoutCompleted.visibility = View.GONE
                        updateEmptyStates(
                            inProgressAdapter.currentList.isEmpty(),
                            completedAdapter.currentList.isEmpty()
                        )
                    }
                    1 -> {
                        binding.layoutInProgress.visibility = View.GONE
                        binding.layoutCompleted.visibility = View.VISIBLE
                        updateEmptyStates(
                            inProgressAdapter.currentList.isEmpty(),
                            completedAdapter.currentList.isEmpty()
                        )
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerViews() {
        inProgressAdapter = MatchAdapter(
            onMatchClick = { match ->
                val action = MatchListFragmentDirections.actionMatchListFragmentToScoreboardFragment(match.id)
                findNavController().navigate(action)
            },
            onDuplicateClick = { match ->
                viewModel.duplicateMatch(match.id)
            },
            onDeleteClick = { match ->
                showDeleteConfirmation(match.id)
            }
        )

        completedAdapter = MatchAdapter(
            onMatchClick = { match ->
                val action = MatchListFragmentDirections.actionMatchListFragmentToScoreboardFragment(match.id)
                findNavController().navigate(action)
            },
            onDuplicateClick = { match ->
                viewModel.duplicateMatch(match.id)
            },
            onDeleteClick = { match ->
                showDeleteConfirmation(match.id)
            }
        )

        binding.recyclerViewInProgress.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = inProgressAdapter
        }

        binding.recyclerViewCompleted.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = completedAdapter
        }
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_matchListFragment_to_newMatchFragment)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    inProgressAdapter.submitList(state.inProgressMatches)
                    completedAdapter.submitList(state.completedMatches)

                    // Update empty states visibility
                    updateEmptyStates(state.inProgressMatches.isEmpty(), state.completedMatches.isEmpty())
                }
            }
        }
    }

    private fun updateEmptyStates(inProgressEmpty: Boolean, completedEmpty: Boolean) {
        // Check which tab is selected
        val selectedTab = binding.tabLayout.selectedTabPosition

        if (selectedTab == 0) {
            // In Progress tab
            binding.recyclerViewInProgress.visibility = if (inProgressEmpty) View.GONE else View.VISIBLE
            binding.emptyStateInProgress.visibility = if (inProgressEmpty) View.VISIBLE else View.GONE
        } else {
            // Completed tab
            binding.recyclerViewCompleted.visibility = if (completedEmpty) View.GONE else View.VISIBLE
            binding.emptyStateCompleted.visibility = if (completedEmpty) View.VISIBLE else View.GONE
        }
    }


    private fun showDeleteConfirmation(matchId: Long) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_match_title)
            .setMessage(R.string.delete_match_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.deleteMatch(matchId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


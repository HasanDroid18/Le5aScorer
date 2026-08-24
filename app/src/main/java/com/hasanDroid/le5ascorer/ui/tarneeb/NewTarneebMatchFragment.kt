package com.hasanDroid.le5ascorer.ui.tarneeb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentNewTarneebMatchBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import com.hasanDroid.le5ascorer.ui.common.applySystemBarMargins
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * New Tarneeb match: name the two teams, pick a target.
 *
 * Mirrors the Leekha setup screen's behaviour — recent-name chips, type-ahead
 * from history, a duplicate-name error rather than a silently disabled button —
 * with two slots instead of four.
 */
@AndroidEntryPoint
class NewTarneebMatchFragment : Fragment() {

    private var _binding: FragmentNewTarneebMatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewTarneebMatchViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewTarneebMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setupFields()
        setupTargetToggle()
        binding.buttonCreate.setOnClickListener { viewModel.createMatch() }
        observe()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)
        // Margins, not padding: padding would stretch the button downward under
        // the gesture bar instead of lifting it clear of one.
        binding.buttonCreate.applySystemBarMargins(bottom = true, sides = false)
    }

    private fun fields(): List<MaterialAutoCompleteTextView> =
        listOf(binding.editTeamA, binding.editTeamB)

    private fun setupFields() {
        fields().forEachIndexed { index, field ->
            field.doAfterTextChanged { viewModel.setTeamName(index, it?.toString().orEmpty()) }
        }
    }

    private fun setupTargetToggle() {
        binding.toggleTerminalScore.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setTerminalScore(
                when (checkedId) {
                    R.id.button31 -> TARGET_31
                    R.id.button61 -> TARGET_61
                    else -> TARGET_41
                }
            )
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.uiState, viewModel.knownTeams) { state, teams ->
                    state to teams
                }.collect { (state, teams) ->
                    render(state, teams)

                    state.createdMatchId?.let { id ->
                        viewModel.onNavigated()
                        findNavController().navigate(
                            NewTarneebMatchFragmentDirections
                                .actionNewTarneebMatchFragmentToTarneebScoreboardFragment(id)
                        )
                    }
                }
            }
        }
    }

    private fun render(state: NewTarneebMatchUiState, teams: List<String>) {
        bindSuggestions(teams)
        bindChips(teams)

        // Say why Create is unavailable instead of leaving a dead button. An
        // empty field explains itself; two identical names do not.
        binding.layoutTeamB.error =
            if (state.hasDuplicateNames) getString(R.string.tarneeb_duplicate_team_names) else null

        binding.buttonCreate.isEnabled = state.isComplete && !state.isCreating
    }

    private fun bindSuggestions(teams: List<String>) {
        fields().forEachIndexed { index, field ->
            val suggestions = viewModel.suggestionsFor(index, teams)
            field.setAdapter(
                ArrayAdapter(requireContext(), R.layout.item_suggestion, suggestions)
            )
        }
    }

    /** One tap fills the first empty slot — the common case is a rematch. */
    private fun bindChips(teams: List<String>) {
        val hasHistory = teams.isNotEmpty()
        binding.textRecentTeamsLabel.visibility = if (hasHistory) View.VISIBLE else View.GONE
        binding.scrollRecentTeams.visibility = if (hasHistory) View.VISIBLE else View.GONE
        if (!hasHistory) return

        val group = binding.chipGroupRecentTeams
        // Rebuilding only when the set changed keeps a tap from clearing itself
        // as the flow re-emits.
        val current = (0 until group.childCount).map { (group.getChildAt(it) as Chip).text.toString() }
        if (current == teams) return

        group.removeAllViews()
        teams.forEach { name ->
            val chip = Chip(requireContext()).apply {
                text = name
                isCheckable = false
                setOnClickListener {
                    val slot = viewModel.firstEmptySlot()
                    if (slot >= 0) fields()[slot].setText(name)
                }
            }
            group.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val TARGET_31 = 31
        const val TARGET_41 = 41
        const val TARGET_61 = 61
    }
}

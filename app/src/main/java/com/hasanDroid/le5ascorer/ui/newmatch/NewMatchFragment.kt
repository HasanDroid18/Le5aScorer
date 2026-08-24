package com.hasanDroid.le5ascorer.ui.newmatch

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentNewMatchBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import com.hasanDroid.le5ascorer.ui.common.applySystemBarMargins
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val ENTRANCE_RISE = 56f
private const val MAX_RECENT_CHIPS = 8

@AndroidEntryPoint
class NewMatchFragment : Fragment() {

    private var _binding: FragmentNewMatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewMatchViewModel by viewModels()

    private var wasButtonEnabled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupToolbar()
        setupInputs()
        setupTerminalScoreSelector()
        setupCreateButton()
        observeUiState()
        observeKnownPlayers()
        playEntranceAnimation()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)
        binding.buttonCreate.applySystemBarMargins(bottom = true, sides = false)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun nameFields(): List<MaterialAutoCompleteTextView> = listOf(
        binding.editPlayer1, binding.editPlayer2, binding.editPlayer3, binding.editPlayer4
    )

    private fun setupInputs() {
        val updaters = listOf(
            viewModel::updatePlayer1Name,
            viewModel::updatePlayer2Name,
            viewModel::updatePlayer3Name,
            viewModel::updatePlayer4Name
        )
        nameFields().forEachIndexed { seat, field ->
            field.addTextChangedListener { updaters[seat](it.toString()) }
            // Refresh the offered names on focus: which ones are still free
            // depends on what the other three seats currently hold.
            field.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) refreshSuggestions(seat)
            }
        }
    }

    private fun refreshSuggestions(seat: Int) {
        val field = nameFields()[seat]
        val suggestions = viewModel.suggestionsFor(seat)
        field.setAdapter(
            ArrayAdapter(requireContext(), R.layout.item_suggestion, suggestions)
        )
    }

    private fun observeKnownPlayers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.knownPlayers.collect { names ->
                    bindRecentChips(names)
                    nameFields().indices.forEach { refreshSuggestions(it) }
                }
            }
        }
    }

    /** One-tap fill for the common case: the same group playing again. */
    private fun bindRecentChips(names: List<String>) {
        binding.layoutRecentPlayers.visibility =
            if (names.isEmpty()) View.GONE else View.VISIBLE
        binding.chipGroupRecent.removeAllViews()

        names.take(MAX_RECENT_CHIPS).forEach { name ->
            val chip = Chip(requireContext()).apply {
                setChipDrawable(
                    com.google.android.material.chip.ChipDrawable.createFromAttributes(
                        requireContext(), null, 0, R.style.Widget_Le5a_Chip_Action
                    )
                )
                text = name
                setChipIconResource(R.drawable.ic_person)
                isChipIconVisible = true
                setOnClickListener { fillNextEmptySeat(name) }
            }
            binding.chipGroupRecent.addView(chip)
        }
    }

    private fun fillNextEmptySeat(name: String) {
        // Already seated somewhere: tapping again would duplicate the player.
        if (viewModel.uiState.value.names.any { it.trim().equals(name, ignoreCase = true) }) return
        val seat = viewModel.firstEmptySeat() ?: return
        nameFields()[seat].setText(name)
    }

    private fun setupTerminalScoreSelector() {
        binding.toggleTerminalScore.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                viewModel.updateTerminalScore(
                    when (checkedId) {
                        R.id.button51 -> 51
                        R.id.button151 -> 151
                        else -> 101
                    }
                )
            }
        }
        binding.toggleTerminalScore.check(R.id.button101)
    }

    private fun setupCreateButton() {
        binding.buttonCreate.setOnClickListener { viewModel.createMatch() }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val isEnabled = state.isValid && !state.isCreating
                    binding.buttonCreate.isEnabled = isEnabled

                    // Say *why* the button is off when the reason is a clash
                    // rather than a blank — a blank field is self-evident.
                    val filled = state.names.map { it.trim() }.filter { it.isNotEmpty() }
                    val hasDuplicate = filled.map { it.lowercase() }.toSet().size != filled.size
                    binding.textNameError.visibility =
                        if (hasDuplicate) View.VISIBLE else View.GONE

                    if (isEnabled && !wasButtonEnabled) animateButtonEnabled()
                    wasButtonEnabled = isEnabled

                    if (state.createdMatchId != null) {
                        findNavController().navigate(
                            NewMatchFragmentDirections
                                .actionNewMatchFragmentToScoreboardFragment(state.createdMatchId)
                        )
                        viewModel.resetCreatedMatchId()
                    }
                }
            }
        }
    }

    /**
     * Cards rise in sequence, then the primary action. Views are referenced by
     * id rather than by child index — the previous version walked
     * `getChildAt(1).getChildAt(0)` and would have animated the wrong views the
     * moment the layout's child order changed.
     */
    private fun playEntranceAnimation() {
        listOf(binding.cardPlayers, binding.cardRules).forEachIndexed { index, child ->
            child.alpha = 0f
            child.translationY = ENTRANCE_RISE
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setStartDelay(index * 90L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }

        binding.buttonCreate.alpha = 0f
        binding.buttonCreate.translationY = ENTRANCE_RISE * 1.5f
        binding.buttonCreate.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(280)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun animateButtonEnabled() {
        val scaleX = ObjectAnimator.ofFloat(binding.buttonCreate, View.SCALE_X, 1f, 1.05f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.buttonCreate, View.SCALE_Y, 1f, 1.05f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 350
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

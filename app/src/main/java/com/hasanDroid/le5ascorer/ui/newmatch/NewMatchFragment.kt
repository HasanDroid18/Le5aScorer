package com.hasanDroid.le5ascorer.ui.newmatch

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.databinding.FragmentNewMatchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

        setupToolbar()
        setupInputs()
        setupTerminalScoreSelector()
        setupScoreRuleSelector()
        setupCreateButton()
        observeUiState()
        playEntranceAnimation()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupInputs() {
        binding.editPlayer1.addTextChangedListener {
            viewModel.updatePlayer1Name(it.toString())
        }
        binding.editPlayer2.addTextChangedListener {
            viewModel.updatePlayer2Name(it.toString())
        }
        binding.editPlayer3.addTextChangedListener {
            viewModel.updatePlayer3Name(it.toString())
        }
        binding.editPlayer4.addTextChangedListener {
            viewModel.updatePlayer4Name(it.toString())
        }
    }

    private fun setupTerminalScoreSelector() {
        binding.toggleTerminalScore.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val score = when (checkedId) {
                    R.id.button51 -> 51
                    R.id.button101 -> 101
                    R.id.button151 -> 151
                    else -> 101
                }
                viewModel.updateTerminalScore(score)
            }
        }
        binding.toggleTerminalScore.check(R.id.button101)
    }

    private fun setupScoreRuleSelector() {
        binding.toggleScoreRule.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val rule = when (checkedId) {
                    R.id.buttonIndividual -> ScoreRule.INDIVIDUAL
                    R.id.buttonTeam -> ScoreRule.TEAM
                    else -> ScoreRule.INDIVIDUAL
                }
                viewModel.updateScoreRule(rule)
                updateRuleDescription(rule)
            }
        }
        binding.toggleScoreRule.check(R.id.buttonIndividual)
    }

    private fun updateRuleDescription(rule: ScoreRule) {
        binding.textRuleDescription.text = when (rule) {
            ScoreRule.INDIVIDUAL -> getString(R.string.rule_individual_desc)
            ScoreRule.TEAM -> getString(R.string.rule_team_desc)
        }
    }

    private fun setupCreateButton() {
        binding.buttonCreate.setOnClickListener {
            viewModel.createMatch()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val isEnabled = state.isValid && !state.isCreating
                    binding.buttonCreate.isEnabled = isEnabled

                    // Pulse animation when button first becomes enabled
                    if (isEnabled && !wasButtonEnabled) {
                        animateButtonEnabled()
                    }
                    wasButtonEnabled = isEnabled

                    if (state.createdMatchId != null) {
                        val action = NewMatchFragmentDirections
                            .actionNewMatchFragmentToScoreboardFragment(state.createdMatchId)
                        findNavController().navigate(action)
                        viewModel.resetCreatedMatchId()
                    }
                }
            }
        }
    }

    private fun playEntranceAnimation() {
        val scrollContent = (binding.root as ViewGroup).getChildAt(1) // NestedScrollView
        val contentContainer = (scrollContent as ViewGroup).getChildAt(0) as ViewGroup

        for (i in 0 until contentContainer.childCount) {
            val child = contentContainer.getChildAt(i)
            child.alpha = 0f
            child.translationY = 60f

            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setStartDelay((i * 100L))
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }

        // Animate the create button sliding up
        binding.buttonCreate.alpha = 0f
        binding.buttonCreate.translationY = 80f
        binding.buttonCreate.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(350)
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


package com.hasanDroid.le5ascorer.ui.scoreboard

import android.content.Context
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
import com.airbnb.lottie.LottieAnimationView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentScoreboardBinding
import com.hasanDroid.le5ascorer.ui.common.EndGameDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScoreboardFragment : Fragment() {

    private var _binding: FragmentScoreboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScoreboardViewModel by viewModels()
    private val args: ScoreboardFragmentArgs by navArgs()

    private lateinit var adapter: ScoreboardAdapter

    private var endGameDialogShown = false

    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScoreboardBinding.inflate(inflater, container, false)

        // Ensure overlay is hidden when returning to this screen
        binding.confettiOverlay.root.visibility = View.GONE

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupAddRoundButton()

        viewModel.loadMatch(args.matchId)
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = ScoreboardAdapter(
            onEditRound = { roundId ->
                // Navigate to edit round
                val matchDetail = viewModel.uiState.value.matchDetail ?: return@ScoreboardAdapter
                val playerNames = matchDetail.match.players.map { it.name }.toTypedArray()

                val action = ScoreboardFragmentDirections
                    .actionScoreboardFragmentToRoundEntryFragment(
                        matchId = args.matchId,
                        roundId = roundId,
                        playerNames = playerNames
                    )
                findNavController().navigate(action)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ScoreboardFragment.adapter
        }
    }

    private fun setupAddRoundButton() {
        binding.buttonAddRound.setOnClickListener {
            val matchDetail = viewModel.uiState.value.matchDetail ?: return@setOnClickListener
            val playerNames = matchDetail.match.players.map { it.name }.toTypedArray()

            val action = ScoreboardFragmentDirections
                .actionScoreboardFragmentToRoundEntryFragment(
                    matchId = args.matchId,
                    roundId = -1L,
                    playerNames = playerNames
                )
            findNavController().navigate(action)
        }

        binding.buttonBackToHome.setOnClickListener {
            // Navigate back to match list (home screen)
            findNavController().navigate(R.id.action_scoreboardFragment_to_matchListFragment)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.matchDetail?.let { detail ->
                        binding.toolbar.title = "Scoreboard"

                        // Set player names in header
                        val playerNames = detail.match.players.map { it.name }
                        binding.textHeaderPlayer1.text = playerNames.getOrNull(0) ?: "P1"
                        binding.textHeaderPlayer2.text = playerNames.getOrNull(1) ?: "P2"
                        binding.textHeaderPlayer3.text = playerNames.getOrNull(2) ?: "P3"
                        binding.textHeaderPlayer4.text = playerNames.getOrNull(3) ?: "P4"

                        // Submit data to adapter
                        adapter.submitData(playerNames, detail.scoreboard, detail.rounds)

                        // Show/hide empty state
                        if (detail.scoreboard.isEmpty()) {
                            binding.emptyState.root.visibility = View.VISIBLE
                            binding.recyclerView.visibility = View.GONE
                            binding.cardPlayerNames.visibility = View.VISIBLE
                        } else {
                            binding.emptyState.root.visibility = View.GONE
                            binding.recyclerView.visibility = View.VISIBLE
                            binding.cardPlayerNames.visibility = View.VISIBLE
                        }

                        // Check if game is over (someone reached target = LOSER)
                        if (state.gameOver != null) {
                            // Game is over, show loser and hide Add Round button
                            binding.cardWinner.visibility = View.VISIBLE
                            binding.textWinner.text = getString(
                                R.string.label_loser_names,
                                state.gameOver.loserNames.joinToString(", ")
                            )
                            binding.buttonAddRound.visibility = View.GONE
                            binding.buttonBackToHome.visibility = View.VISIBLE

                            // Only show the dialog when the match is actually completed,
                            // and only once per match (persisted).
                            val matchCompleted = detail.match.status ==
                                com.hasanDroid.le5ascorer.data.local.entity.MatchStatus.COMPLETED

                            // Show when just completed OR when editing changed who lost.
                            val loserSignature = state.gameOver.loserNames.joinToString("|")
                            val shouldShowForLoserChange =
                                matchCompleted && loserSignature != getLastShownLoserSignature(detail.match.id)

                            if (shouldShowForLoserChange) {
                                setLastShownLoserSignature(detail.match.id, loserSignature)
                                playFullScreenConfetti()
                                showEndGameDialog(state.gameOver.loserNames)
                            }
                        } else {
                            // Game is no longer over (after editing) -> clear signature
                            detail.match.let {
                                clearLastShownLoserSignature(it.id)
                            }

                            // Game in progress, show Add Round button
                            binding.cardWinner.visibility = View.GONE
                            binding.buttonAddRound.visibility = View.VISIBLE
                            binding.buttonBackToHome.visibility = View.GONE

                            endGameDialogShown = false
                        }
                    }

                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun getLastShownLoserSignature(matchId: Long): String? {
        return prefs.getString(lastLoserKey(matchId), null)
    }

    private fun setLastShownLoserSignature(matchId: Long, signature: String) {
        prefs.edit().putString(lastLoserKey(matchId), signature).apply()
    }

    private fun clearLastShownLoserSignature(matchId: Long) {
        prefs.edit().remove(lastLoserKey(matchId)).apply()
    }

    private fun lastLoserKey(matchId: Long): String = "end_game_last_loser_$matchId"

    private fun showEndGameDialog(loserNames: List<String>) {
        val dialog = EndGameDialogFragment.newInstance(loserNames).apply {
            onShowRoundScores = {
                // User asked: go to scoreboard (i.e., keep/show this screen), not to a new round.
                // To make it feel responsive, we just scroll to the top.
                binding.scrollView.smoothScrollTo(0, 0)
            }
        }

        dialog.show(childFragmentManager, "EndGameDialog")
    }

    private fun playFullScreenConfetti() {
        val overlay = binding.confettiOverlay.root
        val lottie = overlay.findViewById<LottieAnimationView>(R.id.lottieConfetti)

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(180).start()

        lottie.setAnimation(R.raw.confetti)
        lottie.repeatCount = 0
        lottie.playAnimation()

        // Fade out the overlay shortly after the animation finishes
        lottie.addAnimatorListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                overlay.animate()
                    .alpha(0f)
                    .setDuration(350)
                    .withEndAction {
                        overlay.visibility = View.GONE
                    }
                    .start()
                lottie.removeAllAnimatorListeners()
            }
        })
    }

    companion object {
        private const val PREFS_NAME = "le5a_scorer_prefs"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

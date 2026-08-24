package com.hasanDroid.le5ascorer.ui.scoreboard

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import com.hasanDroid.le5ascorer.domain.model.MatchDetail
import com.hasanDroid.le5ascorer.ui.common.EndGameDialogFragment
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
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

        applyInsets()
        setupToolbar()
        setupEmptyState()
        setupRecyclerView()
        setupAddRoundButton()

        viewModel.loadMatch(args.matchId)
        observeUiState()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.layoutButtons.applySystemBarInsets(bottom = true)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupEmptyState() {
        binding.emptyState.imageEmpty.setImageResource(R.drawable.ic_cards_empty)
        binding.emptyState.textEmptyTitle.setText(R.string.no_rounds_yet)
        binding.emptyState.textEmptyBody.setText(R.string.no_rounds_yet_hint)
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
                        bindStandings(detail)

                        adapter.submitData(detail.scoreboard, detail.rounds)

                        val hasRounds = detail.scoreboard.isNotEmpty()
                        binding.emptyState.root.visibility =
                            if (hasRounds) View.GONE else View.VISIBLE
                        binding.recyclerView.visibility =
                            if (hasRounds) View.VISIBLE else View.GONE

                        // Check if game is over (someone reached target = LOSER)
                        if (state.gameOver != null) {
                            // Game is over, show loser and hide Add Round button
                            binding.cardWinner.visibility = View.VISIBLE
                            binding.textWinner.text = getString(
                                R.string.match_over_loser,
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

    /**
     * The header's live standings: each player's running total and how far it
     * has travelled toward the target. Reaching the target is how you lose, so
     * the *lowest* total is the good end — the leader is green and the trailing
     * player red, matching the match card in the list.
     *
     * When everyone is level there is no best or worst, so nothing is coloured.
     */
    private fun bindStandings(detail: MatchDetail) {
        val names = detail.match.players.map { it.name }
        val lastRound = detail.scoreboard.lastOrNull()?.playerScores
        val columns = listOf(
            binding.column1, binding.column2, binding.column3, binding.column4
        )
        // Guard the divisor: a match can only be created with a positive target,
        // but the progress indicator would throw on a max of zero.
        val target = detail.match.terminalScore.coerceAtLeast(1)
        val totals = columns.indices.map { lastRound?.getOrNull(it)?.cumulativeScore ?: 0 }
        val max = totals.max()
        val min = totals.min()

        binding.textTargetLabel.text =
            getString(R.string.scoreboard_target_format, detail.match.terminalScore)

        columns.forEachIndexed { index, column ->
            val name = names.getOrNull(index).orEmpty()
            val total = totals[index]
            val colorRes = when {
                max == min -> R.color.text_primary
                total == max -> R.color.danger_bright
                total == min -> R.color.success
                else -> R.color.text_primary
            }
            val color = ContextCompat.getColor(requireContext(), colorRes)

            column.textColumnName.text = name
            column.textColumnTotal.text = total.toString()
            column.textColumnTotal.setTextColor(color)
            column.progressColumn.setIndicatorColor(color)
            column.progressColumn.max = target
            column.progressColumn.progress = total.coerceIn(0, target)

            // One description for the whole column, so a screen reader reads
            // "Ahmad: 45 of 101" instead of the name, the number and the bar.
            column.root.contentDescription =
                getString(R.string.cd_standing, name, total, detail.match.terminalScore)
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
          val dialog = EndGameDialogFragment.newInstance(loserNames, args.matchId).apply {
              onShowRoundScores = {
                  // User asked: go to scoreboard (i.e., keep/show this screen), not to a new round.
                  // To make it feel responsive, we just scroll to the top.
                  binding.scrollView.smoothScrollTo(0, 0)
              }
              onPhotoSaved = { imagePath ->
                  // Photo was saved, update the match with this image path
                  viewModel.saveLoserImage(imagePath)
                  // Navigate to scores view by scrolling to top
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

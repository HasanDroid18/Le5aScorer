package com.hasanDroid.le5ascorer.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.databinding.FragmentHomeBinding
import com.hasanDroid.le5ascorer.databinding.ViewGameTileBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint

/**
 * The game picker, and the app's start destination.
 *
 * Two games are playable and two are announced. The placeholders are shown
 * rather than hidden on purpose — they say what is coming, and a tile that
 * explains itself is better than a surprise later.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyInsets()
        setupToolbar()
        setupTiles()
        setupBackPressHandler()
    }

    private fun applyInsets() {
        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    findNavController().navigate(
                        HomeFragmentDirections.actionHomeFragmentToSettingsFragment()
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTiles() {
        bind(
            binding.tileLeekha,
            R.drawable.ic_suit_heart,
            R.string.game_leekha,
            R.string.game_leekha_subtitle
        ) { openGame(ScoreRule.INDIVIDUAL) }

        bind(
            binding.tileTarneeb,
            R.drawable.ic_suit_spade,
            R.string.game_tarneeb,
            R.string.game_tarneeb_subtitle
        ) { openGame(ScoreRule.TARNEEB) }

        bind(
            binding.tile400,
            R.drawable.ic_suit_club,
            R.string.game_400,
            R.string.game_coming_soon_subtitle
        ) { comingSoon(R.string.game_400) }

        bind(
            binding.tileTrix,
            R.drawable.ic_star,
            R.string.game_trix,
            R.string.game_trix_subtitle
        ) { openGame(ScoreRule.TRIX) }
    }

    private fun bind(
        tile: ViewGameTileBinding,
        @DrawableRes icon: Int,
        @StringRes name: Int,
        @StringRes subtitle: Int,
        onClick: () -> Unit
    ) {
        tile.iconGame.setImageResource(icon)
        tile.textGameName.setText(name)
        tile.textGameSubtitle.setText(subtitle)
        // The description covers the whole tile, so a screen reader announces
        // "Score a game of Leekha" once instead of reading three separate views.
        tile.root.contentDescription = getString(R.string.cd_play_game, getString(name))
        tile.root.setOnClickListener { onClick() }
    }

    /** Every playable game shares one list screen, told apart by this argument. */
    private fun openGame(mode: ScoreRule) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToMatchListFragment(mode.name)
        )
    }

    private fun comingSoon(@StringRes name: Int) {
        Toast.makeText(
            requireContext(),
            getString(R.string.coming_soon_toast, getString(name)),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Back exits the app. This is the home screen now — the behaviour moved here
     * from MatchListFragment, which used to be the start destination and called
     * finish() on the same assumption. Leaving it there would have made the
     * match list a dead end that could never return here.
     */
    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

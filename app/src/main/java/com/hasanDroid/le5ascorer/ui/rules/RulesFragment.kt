package com.hasanDroid.le5ascorer.ui.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentRulesBinding
import com.hasanDroid.le5ascorer.databinding.ViewRulesSectionBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint

/**
 * The rules for every game, one tab each.
 *
 * The text lives in values/strings_rules.xml and is the authority the scoring
 * engines implement, so it is shown verbatim rather than paraphrased. Sections
 * are inflated rather than written out as a static layout, which keeps adding a
 * third game's rules to a single list entry.
 */
@AndroidEntryPoint
class RulesFragment : Fragment() {

    private var _binding: FragmentRulesBinding? = null
    private val binding get() = _binding!!

    /** One headed block of text. */
    private data class Section(@StringRes val title: Int, @StringRes val body: Int)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupTabs()
        show(LEEKHA)
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.rules_leekha_title))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.rules_tarneeb_title))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.rules_trix_title))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                show(
                    when (tab?.position) {
                        1 -> TARNEEB
                        2 -> TRIX
                        else -> LEEKHA
                    }
                )
                // Switching games should start at the top of that game's rules,
                // not wherever the previous tab happened to be scrolled to.
                binding.scrollView.scrollTo(0, 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun show(sections: List<Section>) {
        val container = binding.layoutSections
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val gap = resources.getDimensionPixelSize(R.dimen.space_xl)

        sections.forEachIndexed { index, section ->
            val item = ViewRulesSectionBinding.inflate(inflater, container, false)
            item.textSectionTitle.setText(section.title)
            item.textSectionBody.setText(section.body)
            if (index > 0) {
                (item.root.layoutParams as ViewGroup.MarginLayoutParams).topMargin = gap
            }
            container.addView(item.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val LEEKHA = listOf(
            Section(R.string.rules_leekha_title, R.string.rules_leekha_intro),
            Section(R.string.rules_leekha_cards_title, R.string.rules_leekha_cards),
            Section(R.string.rules_leekha_play_title, R.string.rules_leekha_play),
            Section(R.string.rules_leekha_principle_title, R.string.rules_leekha_principle)
        )

        val TARNEEB = listOf(
            Section(R.string.rules_tarneeb_title, R.string.rules_tarneeb_intro),
            Section(R.string.rules_tarneeb_cards_title, R.string.rules_tarneeb_cards),
            Section(R.string.rules_tarneeb_bidding_title, R.string.rules_tarneeb_bidding),
            Section(R.string.rules_tarneeb_play_title, R.string.rules_tarneeb_play),
            Section(R.string.rules_tarneeb_scoring_title, R.string.rules_tarneeb_scoring)
        )

        val TRIX = listOf(
            Section(R.string.rules_trix_title, R.string.rules_trix_intro),
            Section(R.string.rules_trix_setup_title, R.string.rules_trix_setup),
            Section(R.string.rules_trix_kingdoms_title, R.string.rules_trix_kingdoms),
            Section(R.string.rules_trix_penalties_title, R.string.rules_trix_penalties),
            Section(R.string.rules_trix_trix_title, R.string.rules_trix_trix),
            Section(R.string.rules_trix_doubling_title, R.string.rules_trix_doubling),
            Section(R.string.rules_trix_early_title, R.string.rules_trix_early),
            Section(R.string.rules_trix_winning_title, R.string.rules_trix_winning)
        )
    }
}

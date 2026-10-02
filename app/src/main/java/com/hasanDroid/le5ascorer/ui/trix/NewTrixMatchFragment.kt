package com.hasanDroid.le5ascorer.ui.trix

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentNewTrixMatchBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import com.hasanDroid.le5ascorer.ui.common.applySystemBarMargins
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** New Trix game: four players by team, doubling, then the 7 of Hearts holder. */
@AndroidEntryPoint
class NewTrixMatchFragment : Fragment() {

    private var _binding: FragmentNewTrixMatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewTrixMatchViewModel by viewModels()

    /** On the 7♥ step, Back returns to the names instead of leaving the screen. */
    private val backToNames = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.backToNames()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewTrixMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)
        binding.buttonStart.applySystemBarMargins(bottom = true, sides = false)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backToNames)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        setupFields()
        setupOpeners()
        binding.switchDoubling.setOnCheckedChangeListener { _, checked -> viewModel.setDoubling(checked) }
        binding.buttonStart.setOnClickListener { viewModel.start() }
        observe()
    }

    private fun fields(): List<MaterialAutoCompleteTextView> =
        listOf(binding.editPlayer1, binding.editPlayer2, binding.editPlayer3, binding.editPlayer4)

    private fun openerButtons(): List<MaterialButton> =
        listOf(binding.buttonOpener0, binding.buttonOpener1, binding.buttonOpener2, binding.buttonOpener3)

    private fun setupFields() {
        fields().forEachIndexed { seat, field ->
            field.doAfterTextChanged { viewModel.setName(seat, it?.toString().orEmpty()) }
            // Which names are still free depends on the other three seats.
            field.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    field.setAdapter(
                        ArrayAdapter(requireContext(), R.layout.item_suggestion, viewModel.suggestionsFor(seat))
                    )
                }
            }
        }
    }

    private fun setupOpeners() {
        openerButtons().forEachIndexed { seat, button ->
            button.setOnClickListener {
                viewModel.setOpener(seat)
                // A checkable button unchecks itself on a second tap; the
                // selection is the state's, so put it back.
                button.isChecked = true
            }
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                    state.createdMatchId?.let { id ->
                        viewModel.onNavigated()
                        findNavController().navigate(
                            NewTrixMatchFragmentDirections
                                .actionNewTrixMatchFragmentToTrixScoreboardFragment(id)
                        )
                    }
                }
            }
        }
    }

    private fun render(state: NewTrixMatchUiState) {
        binding.layoutSetup.isVisible = !state.choosingOpener
        binding.layoutOpener.isVisible = state.choosingOpener
        backToNames.isEnabled = state.choosingOpener

        binding.textNameError.isVisible = state.hasDuplicateNames
        if (binding.switchDoubling.isChecked != state.doubling) {
            binding.switchDoubling.isChecked = state.doubling
        }

        openerButtons().forEachIndexed { seat, button ->
            button.text = state.names[seat].trim()
            button.isChecked = state.openerSeat == seat
        }

        binding.buttonStart.isEnabled = state.canStart
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

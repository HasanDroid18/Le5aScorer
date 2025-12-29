package com.hasandroid.le5ascorer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hasandroid.le5ascorer.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupSwitches()
        setupPlaceholders()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSwitches() {
        // Load saved preferences (placeholder - could use SharedPreferences or DataStore)
        binding.switchAlternatingOrder.isChecked = false
        binding.switchShowCardScores.isChecked = false

        // Listeners for switches (placeholder - could save to preferences)
        binding.switchAlternatingOrder.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
        }

        binding.switchShowCardScores.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
        }
    }

    private fun setupPlaceholders() {
        binding.layoutContactSupport.setOnClickListener {
            openEmailSupport()
        }

        binding.layoutRateApp.setOnClickListener {
            openPlayStore()
        }
    }

    private fun openEmailSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("hasantahadroid@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Le5a Scorer - Support Request")
            putExtra(Intent.EXTRA_TEXT, """
                App Version: ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}
                Android Version: ${Build.VERSION.RELEASE}
                Device: ${Build.MANUFACTURER} ${Build.MODEL}
                
                Please describe your issue below:
                
            """.trimIndent())
        }

        try {
            startActivity(Intent.createChooser(intent, "Send email via..."))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlayStore() {
        val packageName = requireContext().packageName
        try {
            // Try to open Play Store app
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            // Fallback to browser if Play Store not installed
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to open Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


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
        setupActions()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupActions() {
        binding.layoutContactSupport.setOnClickListener { openEmailSupport() }
        binding.layoutRateApp.setOnClickListener { showRateOrShareChooser() }
    }

    private fun openEmailSupport() {
        val pm = requireContext().packageManager
        val packageName = requireContext().packageName
        val versionName = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val body = buildString {
            appendLine("Hi Support Team,")
            appendLine()
            appendLine("Please describe your issue below:")
            appendLine("--------------------------------")
            appendLine()
            appendLine()
            appendLine("Diagnostics (auto-filled):")
            appendLine("• App: Le5a Scorer $versionName")
            appendLine("• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("• Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("• Locale: ${java.util.Locale.getDefault()}")
        }

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("hasantahadroid@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Le5a Scorer — Support")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Contact support"))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRateOrShareChooser() {
        // Creative, lightweight flow:
        // 1) Prefer Play Store (if available)
        // 2) Also offer a share intent with a fun card-themed message
        val packageName = requireContext().packageName
        val playStoreUri = Uri.parse("market://details?id=$packageName")
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")

        val shareText = """
            I’m keeping score with Le5a Scorer 🎴
            Fast rounds, clean scoreboard.

            Deal yourself a download:
            $webUri
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Le5a Scorer")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        val marketIntent = Intent(Intent.ACTION_VIEW, playStoreUri)
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)

        // Build a chooser that starts with sharing, and includes store options.
        val chooser = Intent.createChooser(shareIntent, "Deal us some love")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(marketIntent, webIntent))

        try {
            startActivity(chooser)
        } catch (_: Exception) {
            try {
                startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to open rating/share", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

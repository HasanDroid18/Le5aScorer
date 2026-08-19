package com.hasanDroid.le5ascorer.ui.settings

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.FragmentSettingsBinding
import com.hasanDroid.le5ascorer.ui.common.applySystemBarInsets
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

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

        binding.appBarLayout.applySystemBarInsets(top = true)
        binding.scrollView.applySystemBarInsets(bottom = true)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.layoutContactSupport.setOnClickListener { openEmailSupport() }
        binding.layoutRateApp.setOnClickListener { showRateOrShareChooser() }

        showVersion()
    }

    private fun packageInfo(): PackageInfo? = try {
        @Suppress("DEPRECATION")
        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun showVersion() {
        val info = packageInfo() ?: return
        val name = info.versionName ?: return
        binding.textVersion.text =
            getString(R.string.app_version_format, name, info.versionCode)
    }

    private fun openEmailSupport() {
        val versionName = packageInfo()?.versionName ?: "unknown"

        val body = getString(
            R.string.support_email_body,
            versionName,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            Build.MANUFACTURER,
            Build.MODEL,
            Locale.getDefault().toString()
        )

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.support_email)))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(
                Intent.createChooser(emailIntent, getString(R.string.support_chooser_title))
            )
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.support_no_email_app, Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * Offers sharing first, with the Play Store listing alongside it, so the
     * action works whether or not the Play app is installed.
     */
    private fun showRateOrShareChooser() {
        val packageName = requireContext().packageName
        val playStoreUri = Uri.parse("market://details?id=$packageName")
        val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, webUri.toString()))
        }

        val marketIntent = Intent(Intent.ACTION_VIEW, playStoreUri)
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)

        val chooser = Intent.createChooser(shareIntent, getString(R.string.share_chooser_title))
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(marketIntent, webIntent))

        try {
            startActivity(chooser)
        } catch (_: Exception) {
            try {
                startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

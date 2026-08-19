package com.hasanDroid.le5ascorer

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hasanDroid.le5ascorer.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableImmersiveMode()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showVersion()
        setupAnimation()
    }

    private fun showVersion() {
        val versionName = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        binding.textVersion.text =
            versionName?.let { getString(R.string.version_format, it) }.orEmpty()
    }

    private fun setupAnimation() {

        binding.textAppName.animate()
            .alpha(1f)
            .setStartDelay(1000)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.lottieAnimation.addAnimatorListener(object : Animator.AnimatorListener {

            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                navigateToMain()
            }

            override fun onAnimationCancel(animation: Animator) {
                navigateToMain()
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })

        binding.root.postDelayed({
            if (!isFinishing) {
                navigateToMain()
            }
        }, 4000)
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()

        @Suppress("DEPRECATION")
        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

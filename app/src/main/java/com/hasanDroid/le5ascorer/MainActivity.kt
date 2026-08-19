package com.hasanDroid.le5ascorer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.hasanDroid.le5ascorer.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Ensure status bar icons/text stay white (consistent across OEMs)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Insets are applied per screen (see ui/common/WindowInsetsExt.kt) rather
        // than as padding on this root. Padding here meant no screen could draw
        // under the status bar, so every app bar stopped short of it and the
        // status bar showed as a flat band of the window background.
    }
}
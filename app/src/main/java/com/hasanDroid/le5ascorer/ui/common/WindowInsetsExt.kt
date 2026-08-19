package com.hasanDroid.le5ascorer.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Per-view window-inset handling.
 *
 * The activity used to pad the whole nav-host root by the system bar insets,
 * which meant no screen could ever draw underneath the status bar — every app
 * bar stopped below it and the status bar showed as a flat band of the window
 * background. Applying insets at the app bar and at the scrolling content
 * instead lets the felt and the app bar run edge to edge while keeping text and
 * controls out from under the system bars.
 *
 * Padding is applied on top of the view's original padding, and the original is
 * captured once, so repeated inset dispatches do not accumulate.
 */
fun View.applySystemBarInsets(
    top: Boolean = false,
    bottom: Boolean = false,
    sides: Boolean = true
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            left = initialLeft + if (sides) bars.left else 0,
            top = initialTop + if (top) bars.top else 0,
            right = initialRight + if (sides) bars.right else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

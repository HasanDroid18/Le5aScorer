package com.hasanDroid.le5ascorer.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import android.view.ViewGroup

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

/**
 * Like [applySystemBarInsets], but moves the view instead of growing it.
 *
 * Padding is the wrong tool for anything whose own edges are visible. Padding a
 * MaterialButton or a FAB does not lift it clear of the gesture bar — it makes
 * the control taller and pushes its label up, while the background still runs
 * underneath the bar. That is exactly what was happening to Create Match, Save
 * Round and the new-match FAB: three symptoms (clipped, oversized, misplaced)
 * from one mistake.
 *
 * Use this for a free-standing control. Use [applySystemBarInsets] for the
 * container behind one, or for scrolling content, where extra padding is
 * precisely what you want.
 *
 * The original margins are captured once, so repeated inset dispatches do not
 * accumulate. Views whose parent does not use margin layout params are left
 * alone rather than crashing.
 */
fun View.applySystemBarMargins(
    top: Boolean = false,
    bottom: Boolean = false,
    sides: Boolean = true
) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val initialLeft = params.leftMargin
    val initialTop = params.topMargin
    val initialRight = params.rightMargin
    val initialBottom = params.bottomMargin

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = initialLeft + if (sides) bars.left else 0
            topMargin = initialTop + if (top) bars.top else 0
            rightMargin = initialRight + if (sides) bars.right else 0
            bottomMargin = initialBottom + if (bottom) bars.bottom else 0
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

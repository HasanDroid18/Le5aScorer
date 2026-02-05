package com.hasanDroid.le5ascorer.ui.common

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.hasanDroid.le5ascorer.R
import kotlin.random.Random

/**
 * Modal end-game dialog.
 *
 * Contract:
 * - Input: loser names (already computed by the engine/viewmodel)
 * - Output: invokes [onShowRoundScores] when CTA is tapped.
 *
 * Notes:
 * - Not cancelable via back press or outside touch.
 * - Shows a fun, light teasing sentence picked randomly.
 */
class EndGameDialogFragment : DialogFragment() {

    var onShowRoundScores: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.Theme_Le5aScorer_TransparentFullscreenDialog)
        val view = layoutInflater.inflate(R.layout.dialog_end_game, null)

        val loserNames = requireArguments().getStringArrayList(ARG_LOSER_NAMES).orEmpty()

        view.findViewById<TextView>(R.id.textLoser).text =
            loserNames.joinToString(", ").ifBlank { getString(R.string.player_default) }

        view.findViewById<TextView>(R.id.textTease).text = pickTeaseLine()

        view.findViewById<View>(R.id.buttonShowRoundScores).setOnClickListener {
            dismissAllowingStateLoss()
            onShowRoundScores?.invoke()
        }

        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(false)

        dialog.setOnShowListener {
            val card = view.findViewById<View>(R.id.card)
            card.alpha = 0f
            card.scaleX = 0.9f
            card.scaleY = 0.9f
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.9f))
                .start()
        }

        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.decorView.setPadding(0, 0, 0, 0)
        }

        return dialog
    }

    private fun pickTeaseLine(): String {
        val lines = listOf(
            "Oof… that one stings a little. Rematch?",
            "It’s not a loss — it’s a dramatic plot twist!",
            "Legend says this was ‘totally calculated’.",
            "No worries… the table remembers everything.",
            "Spicy round! Let’s see how it happened.",
            "Hey, at least it was entertaining!",
            "Your strategy was bold. The cards were bolder.",
            "That was *this* close to greatness… like, millimeters.",
            "Consider this a warm-up for your comeback arc.",
            "The good news: snacks taste better after a dramatic finish.",
            "Okay okay… who shuffled the universe?",
            "Plot twist: the score had other plans.",
            "You didn’t lose — you just donated points generously.",
            "This is the part where you say: ‘Run it back!’",
            "The table demanded chaos. You delivered.",
            "It’s fine. Confidence resets every new match.",
            "Certified cinematic ending. 10/10 drama.",
            "If the cards had a group chat, they’d be giggling.",
            "Tiny setback. Huge comeback loading…",
            "Somebody call the referee… oh wait, it’s just math.",
            "One more game. For science."
        )
        return lines[Random.nextInt(lines.size)]
    }

    companion object {
        private const val ARG_LOSER_NAMES = "loserNames"

        fun newInstance(loserNames: List<String>): EndGameDialogFragment {
            return EndGameDialogFragment().apply {
                arguments = bundleOf(ARG_LOSER_NAMES to ArrayList(loserNames))
            }
        }
    }
}

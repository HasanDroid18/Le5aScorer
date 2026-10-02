package com.hasanDroid.le5ascorer.ui.trix

import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.domain.TrixContractType

/** Partners share a line: "Hasan & Hsen". Seats 0+1 are Team A, 2+3 Team B. */
fun trixTeamNames(playerNames: List<String>): List<String> =
    playerNames.chunked(2) { it.joinToString(" & ") }

@get:StringRes
val TrixContractType.titleRes: Int
    get() = when (this) {
        TrixContractType.KING -> R.string.trix_contract_king
        TrixContractType.QUEENS -> R.string.trix_contract_queens
        TrixContractType.DIAMONDS -> R.string.trix_contract_diamonds
        TrixContractType.LTOOSH -> R.string.trix_contract_ltoosh
        TrixContractType.TRIX -> R.string.trix_contract_trix
    }

@get:DrawableRes
val TrixContractType.iconRes: Int
    get() = when (this) {
        TrixContractType.KING -> R.drawable.ic_suit_heart
        TrixContractType.QUEENS -> R.drawable.ic_suit_spade
        TrixContractType.DIAMONDS -> R.drawable.ic_suit_diamond
        TrixContractType.LTOOSH -> R.drawable.ic_card_stack
        TrixContractType.TRIX -> R.drawable.ic_star
    }

/** Red suits read red; everything else stays on the text colour. */
@get:ColorRes
val TrixContractType.iconTint: Int
    get() = when (this) {
        TrixContractType.KING, TrixContractType.DIAMONDS -> R.color.danger_bright
        else -> R.color.text_primary
    }

/** The scoring line under each contract; the doubled figures only when doubling is played. */
@StringRes
fun TrixContractType.ruleRes(doubling: Boolean): Int = when (this) {
    TrixContractType.KING -> if (doubling) R.string.trix_rule_king_doubling else R.string.trix_rule_king
    TrixContractType.QUEENS -> if (doubling) R.string.trix_rule_queens_doubling else R.string.trix_rule_queens
    TrixContractType.DIAMONDS -> R.string.trix_rule_diamonds
    TrixContractType.LTOOSH -> R.string.trix_rule_ltoosh
    TrixContractType.TRIX -> R.string.trix_rule_trix
}

/** Signed and coloured, so a penalty never reads as a gain. */
fun TextView.showPoints(points: Int) {
    text = when {
        points > 0 -> context.getString(R.string.tarneeb_points_positive, points)
        points < 0 -> context.getString(R.string.tarneeb_points_negative, -points)
        else -> context.getString(R.string.tarneeb_points_zero)
    }
    setTextColor(
        ContextCompat.getColor(
            context,
            when {
                points > 0 -> R.color.success
                points < 0 -> R.color.danger_bright
                else -> R.color.text_tertiary
            }
        )
    )
}

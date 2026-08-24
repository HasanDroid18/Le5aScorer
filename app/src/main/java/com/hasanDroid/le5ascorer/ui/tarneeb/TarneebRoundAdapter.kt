package com.hasanDroid.le5ascorer.ui.tarneeb

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.ItemTarneebRoundBinding
import com.hasanDroid.le5ascorer.databinding.ViewTarneebScoreCellBinding
import com.hasanDroid.le5ascorer.domain.TarneebRoundResult

/**
 * Tarneeb rounds, oldest at the top.
 *
 * Each row says who bid, whether they made it, and what both teams took — the
 * three things you would ask about a hand you did not see.
 */
class TarneebRoundAdapter(
    private val onEditRound: (Long) -> Unit
) : ListAdapter<TarneebRoundResult, TarneebRoundAdapter.ViewHolder>(DIFF) {

    private var teamNames: List<String> = emptyList()
    private var roundIds: Map<Int, Long> = emptyMap()

    fun submitData(
        results: List<TarneebRoundResult>,
        teamNames: List<String>,
        roundIds: Map<Int, Long>
    ) {
        this.teamNames = teamNames
        this.roundIds = roundIds
        submitList(results)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemTarneebRoundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: ItemTarneebRoundBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: TarneebRoundResult) {
            val context = binding.root.context
            binding.textRound.text = (result.roundIndex + 1).toString()

            binding.textBidder.text = teamNames.getOrNull(result.bidderTeam).orEmpty()
            binding.textBidResult.text = context.getString(
                if (result.bidMade) R.string.tarneeb_row_bid_made else R.string.tarneeb_row_bid_failed,
                result.bid
            )
            binding.textBidResult.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (result.bidMade) R.color.success else R.color.ink_red
                )
            )

            bindCell(binding.cellTeamA, result, team = 0)
            bindCell(binding.cellTeamB, result, team = 1)

            val roundId = roundIds[result.roundIndex]
            binding.buttonEdit.setOnClickListener { roundId?.let(onEditRound) }
        }

        private fun bindCell(cell: ViewTarneebScoreCellBinding, result: TarneebRoundResult, team: Int) {
            val context = cell.root.context
            val tricks = result.tricks.getOrElse(team) { 0 }
            val points = result.roundPoints.getOrElse(team) { 0 }
            val total = result.cumulative.getOrElse(team) { 0 }

            // The number alone: the header legend already reads
            // "Tricks · points · total", and "13 tricks" does not fit a column
            // this narrow. The content description below still says the word.
            cell.textCellTricks.text = tricks.toString()
            cell.textCellPoints.applyPoints(points)
            cell.textCellTotal.text = total.toString()

            cell.root.contentDescription = context.getString(
                R.string.cd_tarneeb_cell,
                teamNames.getOrNull(team).orEmpty(),
                tricks,
                points,
                total
            )
        }

        /** Signed and coloured, so a penalty never reads as a gain. */
        private fun TextView.applyPoints(points: Int) {
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
                        points < 0 -> R.color.ink_red
                        else -> R.color.ink_muted
                    }
                )
            )
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TarneebRoundResult>() {
            override fun areItemsTheSame(a: TarneebRoundResult, b: TarneebRoundResult) =
                a.roundIndex == b.roundIndex

            override fun areContentsTheSame(a: TarneebRoundResult, b: TarneebRoundResult) = a == b
        }
    }
}

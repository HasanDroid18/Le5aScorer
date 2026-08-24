package com.hasanDroid.le5ascorer.ui.scoreboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.ItemScoreboardRowBinding
import com.hasanDroid.le5ascorer.databinding.ViewScoreCellBinding
import com.hasanDroid.le5ascorer.domain.model.PlayerScore
import com.hasanDroid.le5ascorer.domain.model.Round
import com.hasanDroid.le5ascorer.domain.model.RoundScores

/**
 * Rounds, newest at the bottom.
 *
 * Backed by [ListAdapter] so an edit to one round diffs to a single rebind
 * instead of `notifyDataSetChanged()` tearing down the whole list and dropping
 * the user's scroll position.
 */
class ScoreboardAdapter(
    private val onEditRound: (Long) -> Unit
) : ListAdapter<RoundScores, ScoreboardAdapter.ScoreboardViewHolder>(DIFF) {

    private var roundEntities: List<Round> = emptyList()

    fun submitData(roundScores: List<RoundScores>, roundList: List<Round>) {
        roundEntities = roundList
        submitList(roundScores)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreboardViewHolder {
        val binding = ItemScoreboardRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ScoreboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScoreboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScoreboardViewHolder(
        private val binding: ItemScoreboardRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(roundScores: RoundScores) {
            binding.textRound.text = (roundScores.roundIndex + 1).toString()

            val scores = roundScores.playerScores
            val cells = listOf(binding.cell1, binding.cell2, binding.cell3, binding.cell4)

            cells.forEachIndexed { index, cell ->
                bindCell(cell, scores.getOrNull(index))
            }

            val roundEntity = roundEntities.find { it.roundIndex == roundScores.roundIndex }
            binding.buttonEdit.setOnClickListener {
                roundEntity?.let { onEditRound(it.id) }
            }
        }

        /**
         * A cell shows what the round cost this player, with their running total
         * underneath. Rows used to carry the running total alone, which told you
         * where everyone stood but never what actually happened in the round.
         *
         * A player who took nothing gets an em dash rather than "0": in a hand
         * where one person eats thirteen hearts, three zeroes competing for
         * attention is noise.
         */
        private fun bindCell(cell: ViewScoreCellBinding, score: PlayerScore?) {
            val context = cell.root.context
            val roundScore = score?.roundScore ?: 0
            val total = score?.cumulativeScore ?: 0

            cell.textCellRound.text = if (roundScore == 0) {
                context.getString(R.string.scoreboard_no_points)
            } else {
                context.getString(R.string.scoreboard_round_points_format, roundScore)
            }
            cell.textCellRound.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (roundScore == 0) R.color.ink_muted else R.color.ink_red
                )
            )
            cell.textCellTotal.text = total.toString()

            // One description for the pair: two separate ones would make a
            // screen reader read every column twice.
            cell.root.contentDescription = score?.let {
                context.getString(R.string.cd_score_cell, it.playerName, roundScore, total)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<RoundScores>() {
            override fun areItemsTheSame(oldItem: RoundScores, newItem: RoundScores) =
                oldItem.roundIndex == newItem.roundIndex

            override fun areContentsTheSame(oldItem: RoundScores, newItem: RoundScores) =
                oldItem == newItem
        }
    }
}

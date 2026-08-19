package com.hasanDroid.le5ascorer.ui.scoreboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.ItemScoreboardRowBinding
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
            val cells = listOf(
                binding.textPlayer1, binding.textPlayer2,
                binding.textPlayer3, binding.textPlayer4
            )
            val values = cells.indices.map { scores.getOrNull(it)?.cumulativeScore ?: 0 }

            val max = values.max()
            val min = values.min()
            cells.forEachIndexed { index, cell ->
                cell.text = values[index].toString()
                cell.contentDescription = scores.getOrNull(index)
                    ?.let { cell.context.getString(R.string.cd_player_column, it.playerName) }
                highlight(cell, values[index], max, min)
            }

            val roundEntity = roundEntities.find { it.roundIndex == roundScores.roundIndex }
            binding.buttonEdit.setOnClickListener {
                roundEntity?.let { onEditRound(it.id) }
            }
        }

        /**
         * Highlights the current best and worst totals. When every player is
         * level there is no best or worst, so nothing is highlighted — the
         * previous version tested `max` first and painted all four red.
         */
        private fun highlight(view: TextView, score: Int, max: Int, min: Int) {
            val colorRes = when {
                max == min -> R.color.ink
                score == max -> R.color.ink_red
                score == min -> R.color.success
                else -> R.color.ink
            }
            view.setTextColor(ContextCompat.getColor(view.context, colorRes))
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

package com.hasanDroid.le5ascorer.ui.scoreboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.databinding.ItemScoreboardRowBinding
import com.hasanDroid.le5ascorer.domain.model.Round
import com.hasanDroid.le5ascorer.domain.model.RoundScores

class ScoreboardAdapter(
    private val onEditRound: (Long) -> Unit
) : RecyclerView.Adapter<ScoreboardAdapter.ScoreboardViewHolder>() {

    private var playerNames: List<String> = emptyList()
    private var rounds: List<RoundScores> = emptyList()
    private var roundEntities: List<Round> = emptyList()

    fun submitData(names: List<String>, roundScores: List<RoundScores>, roundList: List<Round>) {
        playerNames = names
        rounds = roundScores
        roundEntities = roundList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rounds.size // No header in adapter

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreboardViewHolder {
        val binding = ItemScoreboardRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScoreboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScoreboardViewHolder, position: Int) {
        holder.bindRound(rounds[position])
    }

    inner class ScoreboardViewHolder(
        private val binding: ItemScoreboardRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {


        fun bindRound(roundScores: RoundScores) {
            binding.textRound.text = (roundScores.roundIndex + 1).toString()

            val scores = roundScores.playerScores
            binding.textPlayer1.text = scores.getOrNull(0)?.cumulativeScore?.toString() ?: "0"
            binding.textPlayer2.text = scores.getOrNull(1)?.cumulativeScore?.toString() ?: "0"
            binding.textPlayer3.text = scores.getOrNull(2)?.cumulativeScore?.toString() ?: "0"
            binding.textPlayer4.text = scores.getOrNull(3)?.cumulativeScore?.toString() ?: "0"

            // Highlight max (red) and min (green) for Individual rule
            val max = scores.maxOfOrNull { it.cumulativeScore } ?: 0
            val min = scores.minOfOrNull { it.cumulativeScore } ?: 0

            highlightScore(binding.textPlayer1, scores.getOrNull(0)?.cumulativeScore ?: 0, max, min)
            highlightScore(binding.textPlayer2, scores.getOrNull(1)?.cumulativeScore ?: 0, max, min)
            highlightScore(binding.textPlayer3, scores.getOrNull(2)?.cumulativeScore ?: 0, max, min)
            highlightScore(binding.textPlayer4, scores.getOrNull(3)?.cumulativeScore ?: 0, max, min)

            // Edit button click listener - find the Round entity by roundIndex
            val roundEntity = roundEntities.find { it.roundIndex == roundScores.roundIndex }
            binding.buttonEdit.setOnClickListener {
                roundEntity?.let { onEditRound(it.id) }
            }
        }

        private fun highlightScore(textView: android.widget.TextView, score: Int, max: Int, min: Int) {
            when (score) {
                max -> textView.setTextColor(textView.context.getColor(android.R.color.holo_red_dark))
                min -> textView.setTextColor(textView.context.getColor(android.R.color.holo_green_dark))
                else -> textView.setTextColor(textView.context.getColor(android.R.color.black))
            }
        }
    }
}


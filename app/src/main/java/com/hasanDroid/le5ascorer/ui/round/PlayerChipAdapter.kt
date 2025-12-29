package com.hasanDroid.le5ascorer.ui.round

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.ItemPlayerChipBinding

data class PlayerChipItem(
    val playerIndex: Int,
    val playerName: String,
    val score: Int,
    val heartCount: Int,
    val hasQSpades: Boolean,
    val hasTenDiamonds: Boolean,
    val isSelected: Boolean
)

class PlayerChipAdapter(
    private val onPlayerSelected: (Int) -> Unit
) : ListAdapter<PlayerChipItem, PlayerChipAdapter.PlayerChipViewHolder>(PlayerChipDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerChipViewHolder {
        val binding = ItemPlayerChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlayerChipViewHolder(binding, onPlayerSelected)
    }

    override fun onBindViewHolder(holder: PlayerChipViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlayerChipViewHolder(
        private val binding: ItemPlayerChipBinding,
        private val onPlayerSelected: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlayerChipItem) {
            binding.textPlayerName.text = item.playerName
            binding.textPlayerScore.text = item.score.toString()

            // Show card icons based on what player has
            if (item.heartCount > 0) {
                binding.iconHearts.visibility = View.VISIBLE
                binding.textHeartCount.visibility = View.VISIBLE
                binding.textHeartCount.text = "×${item.heartCount}"
            } else {
                binding.iconHearts.visibility = View.GONE
                binding.textHeartCount.visibility = View.GONE
            }

            binding.iconQSpades.visibility = if (item.hasQSpades) View.VISIBLE else View.GONE
            binding.iconTenDiamonds.visibility = if (item.hasTenDiamonds) View.VISIBLE else View.GONE

            // Update chip appearance based on selection
            if (item.isSelected) {
                binding.chipCard.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.chip_gold)
                )
                binding.chipCard.strokeWidth = 6
                binding.chipCard.strokeColor =
                    ContextCompat.getColor(binding.root.context, R.color.chip_gold_dark)
                binding.chipCard.elevation = 12f
                binding.chipCard.scaleX = 1.05f
                binding.chipCard.scaleY = 1.05f
            } else {
                binding.chipCard.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.chip_silver)
                )
                binding.chipCard.strokeWidth = 4
                binding.chipCard.strokeColor =
                    ContextCompat.getColor(binding.root.context, R.color.chip_gold_dark)
                binding.chipCard.elevation = 4f
                binding.chipCard.scaleX = 1.0f
                binding.chipCard.scaleY = 1.0f
            }

            binding.root.setOnClickListener {
                onPlayerSelected(item.playerIndex)
            }
        }
    }

    private class PlayerChipDiffCallback : DiffUtil.ItemCallback<PlayerChipItem>() {
        override fun areItemsTheSame(oldItem: PlayerChipItem, newItem: PlayerChipItem): Boolean {
            return oldItem.playerIndex == newItem.playerIndex
        }

        override fun areContentsTheSame(oldItem: PlayerChipItem, newItem: PlayerChipItem): Boolean {
            return oldItem == newItem
        }
    }
}


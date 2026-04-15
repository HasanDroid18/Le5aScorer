package com.hasanDroid.le5ascorer.ui.matches

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.domain.model.Match
import com.hasanDroid.le5ascorer.databinding.ItemMatchBinding
import java.io.File

class MatchAdapter(
    private val onMatchClick: (Match) -> Unit,
    private val onDuplicateClick: (Match) -> Unit,
    private val onDeleteClick: (Match) -> Unit
) : ListAdapter<Match, MatchAdapter.MatchViewHolder>(MatchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val match = getItem(position)
        holder.bind(match, onMatchClick, onDuplicateClick, onDeleteClick)
    }

    class MatchViewHolder(private val binding: ItemMatchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            match: Match,
            onMatchClick: (Match) -> Unit,
            onDuplicateClick: (Match) -> Unit,
            onDeleteClick: (Match) -> Unit
        ) {
            binding.root.setOnClickListener { onMatchClick(match) }

            // Display player names
            val playerNames = match.players.joinToString(", ") { it.name }
            binding.textPlayers.text = playerNames

            // Display rule summary
            val ruleType = if (match.scoreRule.name == "INDIVIDUAL") "Individual" else "Team"
            binding.textRuleSummary.text = "Terminal: ${match.terminalScore} • $ruleType"

            // Display round count
            val roundText = if (match.roundCount == 1) "1 Round" else "${match.roundCount} Rounds"
            binding.textRounds.text = roundText

            // Display match status
            if (match.roundCount > 0) {
                binding.textStatus.visibility = android.view.View.VISIBLE
                val statusText = when (match.status) {
                    com.hasanDroid.le5ascorer.data.local.entity.MatchStatus.COMPLETED -> "Completed"
                    com.hasanDroid.le5ascorer.data.local.entity.MatchStatus.IN_PROGRESS -> "In Progress"
                }
                binding.textStatus.text = statusText

                // Color status text
                val statusColor = when (match.status) {
                    com.hasanDroid.le5ascorer.data.local.entity.MatchStatus.COMPLETED -> 0xFF4CAF50.toInt() // Green
                    com.hasanDroid.le5ascorer.data.local.entity.MatchStatus.IN_PROGRESS -> 0xFFFFC107.toInt() // Amber
                }
                binding.textStatus.setTextColor(statusColor)
            } else {
                binding.textStatus.visibility = android.view.View.GONE
            }

             // Display leading player and score
             if (match.leadingPlayerName != null && match.leadingScore != null) {
                 binding.cardLeadingPlayer.visibility = android.view.View.VISIBLE
                 binding.textLeaderName.text = match.leadingPlayerName
                 binding.textLeaderScore.text = match.leadingScore.toString()

                 // Load loser image if available, otherwise show default placeholder
                 if (!match.loserImagePath.isNullOrEmpty()) {
                     val imageFile = File(match.loserImagePath)
                     if (imageFile.exists()) {
                         val bitmap = BitmapFactory.decodeFile(match.loserImagePath)
                         binding.loserImage.setImageBitmap(bitmap)
                     } else {
                         // Image file not found, show default
                         binding.loserImage.setImageResource(android.R.drawable.ic_menu_gallery)
                     }
                 } else {
                     // No image captured, show default placeholder
                     binding.loserImage.setImageResource(android.R.drawable.ic_menu_gallery)
                 }
             } else {
                 binding.cardLeadingPlayer.visibility = android.view.View.GONE
             }

            // Display date
            val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            binding.textDate.text = dateFormat.format(java.util.Date(match.createdAt))

            // Setup menu button
            binding.buttonMenu.setOnClickListener {
                showPopupMenu(it, match, onDuplicateClick, onDeleteClick)
            }
        }

        private fun showPopupMenu(
            view: android.view.View,
            match: Match,
            onDuplicateClick: (Match) -> Unit,
            onDeleteClick: (Match) -> Unit
        ) {
            val popup = android.widget.PopupMenu(view.context, view)
            popup.inflate(com.hasanDroid.le5ascorer.R.menu.menu_match_item)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    com.hasanDroid.le5ascorer.R.id.action_duplicate -> {
                        onDuplicateClick(match)
                        true
                    }
                    com.hasanDroid.le5ascorer.R.id.action_delete -> {
                        onDeleteClick(match)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class MatchDiffCallback : DiffUtil.ItemCallback<Match>() {
        override fun areItemsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Match, newItem: Match): Boolean {
            return oldItem == newItem
        }
    }
}


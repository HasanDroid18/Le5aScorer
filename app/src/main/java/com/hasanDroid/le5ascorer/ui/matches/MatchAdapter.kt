package com.hasanDroid.le5ascorer.ui.matches

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.data.local.entity.MatchStatus
import com.hasanDroid.le5ascorer.data.local.entity.ScoreRule
import com.hasanDroid.le5ascorer.databinding.ItemMatchBinding
import com.hasanDroid.le5ascorer.domain.model.Match
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        holder.bind(getItem(position), onMatchClick, onDuplicateClick, onDeleteClick)
    }

    class MatchViewHolder(
        private val binding: ItemMatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context get() = binding.root.context

        fun bind(
            match: Match,
            onMatchClick: (Match) -> Unit,
            onDuplicateClick: (Match) -> Unit,
            onDeleteClick: (Match) -> Unit
        ) {
            binding.root.setOnClickListener { onMatchClick(match) }

            binding.textPlayers.text = match.players.joinToString(", ") { it.name }

            val ruleLabel = context.getString(
                when (match.scoreRule) {
                    ScoreRule.INDIVIDUAL -> R.string.rule_individual
                    ScoreRule.TEAM -> R.string.rule_team
                }
            )
            binding.textRuleSummary.text =
                context.getString(R.string.match_rule_summary, match.terminalScore, ruleLabel)

            binding.textRounds.text = context.resources.getQuantityString(
                R.plurals.match_round_count, match.roundCount, match.roundCount
            )

            bindStatus(match)
            bindLeader(match)

            binding.textDate.text = DATE_FORMAT.format(Date(match.createdAt))

            binding.buttonMenu.setOnClickListener {
                showPopupMenu(it, match, onDuplicateClick, onDeleteClick)
            }
        }

        private fun bindStatus(match: Match) {
            if (match.roundCount == 0) {
                binding.textStatus.visibility = View.GONE
                return
            }
            binding.textStatus.visibility = View.VISIBLE
            val (label, tint) = when (match.status) {
                MatchStatus.COMPLETED -> R.string.completed to R.color.success
                MatchStatus.IN_PROGRESS -> R.string.in_progress to R.color.brass_300
            }
            binding.textStatus.setText(label)
            binding.textStatus.setTextColor(ContextCompat.getColor(context, tint))
        }

        /**
         * The label, name, score and photo are one unit — [R.id.groupLeader]
         * toggles them together, so a match with no leader yet no longer shows
         * a stranded "LEADING" caption above an empty name.
         */
        private fun bindLeader(match: Match) {
            val leaderName = match.leadingPlayerName
            val leaderScore = match.leadingScore
            if (leaderName == null || leaderScore == null) {
                binding.groupLeader.visibility = View.GONE
                return
            }

            binding.groupLeader.visibility = View.VISIBLE
            binding.textLeaderName.text = leaderName
            binding.textLeaderScore.text = leaderScore.toString()

            val photo = match.loserImagePath
                ?.takeIf { it.isNotEmpty() && File(it).exists() }
                ?.let { BitmapFactory.decodeFile(it) }

            if (photo != null) {
                // A real photo fills the tile edge to edge and must not be tinted.
                binding.loserImage.setPadding(0, 0, 0, 0)
                binding.loserImage.imageTintList = null
                binding.loserImage.setImageBitmap(photo)
            } else {
                val inset = context.resources.getDimensionPixelSize(R.dimen.space_lg)
                binding.loserImage.setPadding(inset, inset, inset, inset)
                binding.loserImage.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_tertiary))
                binding.loserImage.setImageResource(R.drawable.ic_person)
            }
        }

        private fun showPopupMenu(
            anchor: View,
            match: Match,
            onDuplicateClick: (Match) -> Unit,
            onDeleteClick: (Match) -> Unit
        ) {
            PopupMenu(anchor.context, anchor).apply {
                inflate(R.menu.menu_match_item)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_duplicate -> {
                            onDuplicateClick(match); true
                        }
                        R.id.action_delete -> {
                            onDeleteClick(match); true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        private companion object {
            val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        }
    }

    class MatchDiffCallback : DiffUtil.ItemCallback<Match>() {
        override fun areItemsTheSame(oldItem: Match, newItem: Match) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Match, newItem: Match) = oldItem == newItem
    }
}

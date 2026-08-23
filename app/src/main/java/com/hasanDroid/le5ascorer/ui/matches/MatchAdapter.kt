package com.hasanDroid.le5ascorer.ui.matches

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.data.local.entity.MatchStatus
import com.hasanDroid.le5ascorer.databinding.ItemMatchBinding
import com.hasanDroid.le5ascorer.databinding.ViewStandingRowBinding
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

            binding.textRuleSummary.text =
                context.getString(R.string.match_rule_summary, match.terminalScore)

            binding.textRounds.text = context.resources.getQuantityString(
                R.plurals.match_round_count, match.roundCount, match.roundCount
            )
            binding.textDate.text = DATE_FORMAT.format(Date(match.createdAt))

            bindStatus(match)
            bindStandings(match)
            bindPhoto(match)

            binding.buttonMenu.setOnClickListener {
                showPopupMenu(it, match, onDuplicateClick, onDeleteClick)
            }
        }

        private fun standingRows(): List<ViewStandingRowBinding> = listOf(
            binding.standing1, binding.standing2, binding.standing3, binding.standing4
        )

        /**
         * All four players with a bar showing progress toward the target.
         *
         * Reaching the target is how you lose, so the fullest bar marks the
         * player in most trouble and the shortest marks whoever is safest.
         * Before the first round there are no scores, so the card falls back to
         * a plain list of names.
         */
        private fun bindStandings(match: Match) {
            val scores = match.playerScores
            val hasScores = scores.size == match.players.size && match.roundCount > 0

            binding.layoutStandings.visibility = if (hasScores) View.VISIBLE else View.GONE
            binding.textPlayers.visibility = if (hasScores) View.GONE else View.VISIBLE

            if (!hasScores) {
                binding.textPlayers.text = match.players.joinToString(", ") { it.name }
                return
            }

            val most = scores.max()
            val fewest = scores.min()

            standingRows().forEachIndexed { index, row ->
                val player = match.players.getOrNull(index)
                val score = scores.getOrNull(index)
                if (player == null || score == null) {
                    row.root.visibility = View.GONE
                    return@forEachIndexed
                }
                row.root.visibility = View.VISIBLE
                row.textStandingName.text = player.name
                row.textStandingScore.text = score.toString()

                row.progressStanding.max = match.terminalScore
                row.progressStanding.progress = score.coerceAtMost(match.terminalScore)

                // Everyone level means nobody is ahead or behind, so no accent.
                val accent = when {
                    most == fewest -> R.color.text_tertiary
                    score == most -> R.color.danger
                    score == fewest -> R.color.success
                    else -> R.color.text_tertiary
                }
                row.pipStanding.backgroundTintList = colorState(accent)
                row.progressStanding.setIndicatorColor(color(accent))
                row.textStandingScore.setTextColor(color(accent))
            }
        }

        private fun bindStatus(match: Match) {
            val completed = match.status == MatchStatus.COMPLETED
            binding.accentEdge.backgroundTintList =
                colorState(if (completed) R.color.success else R.color.brass_400)

            if (match.roundCount == 0) {
                binding.textStatus.visibility = View.GONE
                return
            }
            binding.textStatus.visibility = View.VISIBLE
            binding.textStatus.setText(if (completed) R.string.completed else R.string.in_progress)
            binding.textStatus.setTextColor(
                color(if (completed) R.color.success else R.color.brass_300)
            )
        }

        private fun bindPhoto(match: Match) {
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
                binding.loserImage.imageTintList = colorState(R.color.text_tertiary)
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

        private fun color(@ColorRes res: Int) = ContextCompat.getColor(context, res)
        private fun colorState(@ColorRes res: Int) = ColorStateList.valueOf(color(res))

        private companion object {
            val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        }
    }

    class MatchDiffCallback : DiffUtil.ItemCallback<Match>() {
        override fun areItemsTheSame(oldItem: Match, newItem: Match) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Match, newItem: Match) = oldItem == newItem
    }
}

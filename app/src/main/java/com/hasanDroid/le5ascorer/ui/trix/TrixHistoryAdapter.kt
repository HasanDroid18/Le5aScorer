package com.hasanDroid.le5ascorer.ui.trix

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.databinding.ItemTrixHistoryBinding
import com.hasanDroid.le5ascorer.domain.TrixContractResult

/** Played contracts, oldest first. Tapping edit reopens that contract's entry. */
class TrixHistoryAdapter(
    private val onEdit: (TrixContractResult) -> Unit
) : ListAdapter<TrixContractResult, TrixHistoryAdapter.ViewHolder>(DIFF) {

    private var playerNames: List<String> = emptyList()

    fun submitData(results: List<TrixContractResult>, playerNames: List<String>) {
        this.playerNames = playerNames
        submitList(results)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemTrixHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: ItemTrixHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: TrixContractResult) {
            val context = binding.root.context
            binding.textHistoryContract.setText(result.contract.type.titleRes)
            binding.textHistoryOwner.text = context.getString(
                R.string.trix_history_owner,
                playerNames.getOrNull(result.ownerSeat).orEmpty()
            )
            binding.textHistoryPointsA.showPoints(result.points[0])
            binding.textHistoryPointsB.showPoints(result.points[1])
            binding.buttonEdit.setOnClickListener { onEdit(result) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TrixContractResult>() {
            override fun areItemsTheSame(a: TrixContractResult, b: TrixContractResult) =
                a.roundId == b.roundId

            override fun areContentsTheSame(a: TrixContractResult, b: TrixContractResult) = a == b
        }
    }
}

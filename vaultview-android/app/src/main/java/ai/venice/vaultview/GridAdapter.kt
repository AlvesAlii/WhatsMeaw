package ai.venice.vaultview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ai.venice.vaultview.databinding.ItemMediaBinding
import com.bumptech.glide.Glide
import java.io.File
import java.text.DateFormat
import java.util.Date

class GridAdapter(
    private var items: List<VaultRepo.Item>,
    private val onClick: (VaultRepo.Item) -> Unit,
    private val onLong: (VaultRepo.Item) -> Unit,
) : RecyclerView.Adapter<GridAdapter.VH>() {

    class VH(val b: ItemMediaBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<VaultRepo.Item>) {
        items = list
        notifyDataSetChanged()
    }

    fun currentList(): List<VaultRepo.Item> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        Glide.with(h.b.thumb).load(File(item.path)).centerCrop().into(h.b.thumb)
        h.b.badge.text = if (item.suspicious) "única" else "normal"
        h.b.badge.alpha = if (item.suspicious) 1f else 0.5f
        h.b.date.text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.ts))
        h.b.root.setOnClickListener { onClick(item) }
        h.b.root.setOnLongClickListener { onLong(item); true }
    }
}

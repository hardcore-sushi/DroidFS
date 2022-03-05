package sushi.hardcore.droidfs.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.Volume
import sushi.hardcore.droidfs.VolumeDatabase
import java.io.File

class VolumeAdapter(
    private val context: Context,
    private val volumeDatabase: VolumeDatabase,
    private val allowSelection: Boolean,
    private val showReadOnly: Boolean,
    private val onVolumeItemClick: (Volume, Int) -> Unit,
    private val onVolumeItemLongClick: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    lateinit var volumes: List<Volume>
    val selectedItems: MutableSet<Int> = HashSet()

    init {
        reloadVolumes()
    }

    private fun reloadVolumes() {
        volumes = if (showReadOnly) {
            volumeDatabase.getVolumes()
        } else {
            volumeDatabase.getVolumes().filter { v -> v.canWrite(context.filesDir.path) }
        }
    }

    private fun toggleSelection(position: Int): Boolean {
        return if (selectedItems.contains(position)) {
            selectedItems.remove(position)
            false
        } else {
            selectedItems.add(position)
            true
        }
    }

    private fun onItemClick(position: Int): Boolean {
        onVolumeItemClick(volumes[position], position)
        if (allowSelection && selectedItems.isNotEmpty()) {
            return toggleSelection(position)
        }
        return false
    }

    private fun onItemLongClick(position: Int): Boolean {
        onVolumeItemLongClick()
        return if (allowSelection)
            toggleSelection(position)
        else
            false
    }

    fun onVolumeChanged(position: Int) {
        reloadVolumes()
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll() {
        for (i in volumes.indices) {
            if (!selectedItems.contains(i))
                selectedItems.add(i)
        }
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun unSelectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun refresh() {
        reloadVolumes()
        unSelectAll()
    }

    inner class VolumeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private fun setBackground(isSelected: Boolean) {
            itemView.setBackgroundResource(if (isSelected) R.color.itemSelected else 0)
        }

        fun bind(position: Int) {
            val volume = volumes[position]
            itemView.findViewById<TextView>(R.id.text_volume_name).text = volume.shortName
            itemView.findViewById<ImageView>(R.id.image_icon).setImageResource(R.drawable.icon_volume)
            itemView.findViewById<TextView>(R.id.text_path).text = if (volume.isHidden)
                context.getString(R.string.hidden_volume)
            else
                volume.name
            val canWrite = volume.canWrite(context.filesDir.path)
            val infoString: String? = if (volume.encryptedHash == null)
                if (canWrite) null else '(' + context.getString(R.string.read_only) + ')'
            else
                '(' +
                        (if (canWrite) "" else context.getString(R.string.read_only) + ", ") +
                        context.getString(R.string.password_hash_saved) +
                ')'
            itemView.findViewById<TextView>(R.id.text_info).apply {
                if (infoString == null)
                    visibility = View.GONE
                else {
                    text = infoString
                    visibility = View.VISIBLE
                }
            }
            (bindingAdapter as VolumeAdapter?)?.let { adapter ->
                itemView.findViewById<LinearLayout>(R.id.selectable_container).apply {
                    setOnClickListener {
                        setBackground(adapter.onItemClick(layoutPosition))
                    }
                    setOnLongClickListener {
                        setBackground(adapter.onItemLongClick(layoutPosition))
                        true
                    }
                }
            }
            setBackground(selectedItems.contains(position))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view: View = inflater.inflate(R.layout.adapter_volume, parent, false)
        return VolumeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as VolumeViewHolder).bind(position)
    }

    override fun getItemCount(): Int {
        return volumes.size
    }
}
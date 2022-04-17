package sushi.hardcore.droidfs.adapters

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

class VolumeAdapter(
    private val context: Context,
    private val volumeDatabase: VolumeDatabase,
    private val allowSelection: Boolean,
    private val showReadOnly: Boolean,
    private val listener: Listener,
) : SelectableAdapter<Volume>(listener::onSelectionChanged) {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    lateinit var volumes: List<Volume>

    init {
        reloadVolumes()
    }

    interface Listener {
        fun onSelectionChanged(size: Int)
        fun onVolumeItemClick(volume: Volume, position: Int)
        fun onVolumeItemLongClick()
    }

    override fun getItems(): List<Volume> {
        return volumes
    }

    private fun reloadVolumes() {
        volumes = if (showReadOnly) {
            volumeDatabase.getVolumes()
        } else {
            volumeDatabase.getVolumes().filter { v -> v.canWrite(context.filesDir.path) }
        }
    }

    override fun onItemClick(position: Int): Boolean {
        listener.onVolumeItemClick(volumes[position], position)
        return if (allowSelection) {
            super.onItemClick(position)
        } else {
            false
        }
    }

    override fun onItemLongClick(position: Int): Boolean {
        listener.onVolumeItemLongClick()
        return if (allowSelection)
            super.onItemLongClick(position)
        else
            false
    }

    fun onVolumeChanged(position: Int) {
        reloadVolumes()
        notifyItemChanged(position)
    }

    fun refresh() {
        reloadVolumes()
        unSelectAll(true)
    }

    inner class VolumeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

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
            setSelectable(itemView.findViewById<LinearLayout>(R.id.selectable_container), itemView, layoutPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view: View = inflater.inflate(R.layout.adapter_volume, parent, false)
        return VolumeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as VolumeViewHolder).bind(position)
    }
}
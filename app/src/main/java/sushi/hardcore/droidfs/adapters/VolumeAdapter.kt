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
import sushi.hardcore.droidfs.SavedVolume
import sushi.hardcore.droidfs.VolumeDatabase

class VolumeAdapter(
    private val context: Context,
    private val volumeDatabase: VolumeDatabase,
    private val allowSelection: Boolean,
    private val showReadOnly: Boolean,
    private val listener: Listener,
) : SelectableAdapter<SavedVolume>(listener::onSelectionChanged) {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    lateinit var volumes: List<SavedVolume>

    init {
        reloadVolumes()
    }

    interface Listener {
        fun onSelectionChanged(size: Int)
        fun onVolumeItemClick(volume: SavedVolume, position: Int)
        fun onVolumeItemLongClick()
    }

    override fun getItems(): List<SavedVolume> {
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

    @SuppressLint("NotifyDataSetChanged")
    fun refresh() {
        reloadVolumes()
        unSelectAll(false)
        notifyDataSetChanged()
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
            itemView.findViewById<ImageView>(R.id.icon_fingerprint).visibility = if (volume.encryptedHash == null) {
                View.GONE
            } else {
                View.VISIBLE
            }
            itemView.findViewById<TextView>(R.id.text_read_only).apply {
                visibility = if (volume.canWrite(context.filesDir.path))
                    View.GONE
                else {
                    View.VISIBLE
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
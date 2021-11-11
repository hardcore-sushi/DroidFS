package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.Volume
import sushi.hardcore.droidfs.VolumeDatabase
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.NonScrollableColoredBorderListView
import java.io.File

class SavedVolumesAdapter(private val context: Context, private val themeValue: String, private val volumeDatabase: VolumeDatabase) : BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private lateinit var nonScrollableColoredBorderListView: NonScrollableColoredBorderListView

    override fun getCount(): Int {
        return volumeDatabase.getVolumes().size
    }

    override fun getItem(position: Int): Volume {
        return volumeDatabase.getVolumes()[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    private fun deletePasswordHash(volume: Volume){
        volumeDatabase.removeHash(volume)
        volume.hash = null
        volume.iv = null
    }

    private fun deleteVolumeData(volume: Volume, parent: ViewGroup){
        volumeDatabase.removeVolume(volume)
        refresh(parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (!::nonScrollableColoredBorderListView.isInitialized){
            nonScrollableColoredBorderListView = parent as NonScrollableColoredBorderListView
        }
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_saved_volume, parent, false)
        val volumeNameTextView = view.findViewById<TextView>(R.id.volume_name_textview)
        val currentVolume = getItem(position)
        volumeNameTextView.text = currentVolume.name
        val deleteImageView = view.findViewById<ImageView>(R.id.delete_imageview)
        deleteImageView.imageTintList = ColorStateList.valueOf(nonScrollableColoredBorderListView.colorAccent) //fix a strange bug that sometimes displays the icon in white
        deleteImageView.setOnClickListener {
            val dialog = CustomAlertDialogBuilder(context, themeValue)
            dialog.setTitle(R.string.warning)
            if (currentVolume.isHidden){
                if (currentVolume.hash != null) {
                    dialog.setMessage(R.string.hidden_volume_delete_question_hash)
                    dialog.setPositiveButton(R.string.password_hash){ _, _ ->
                        deletePasswordHash(currentVolume)
                    }
                    dialog.setNegativeButton(R.string.password_hash_and_path){ _, _ ->
                        deleteVolumeData(currentVolume, parent)
                    }
                    dialog.setNeutralButton(R.string.whole_volume){ _, _ ->
                        PathUtils.recursiveRemoveDirectory(File(PathUtils.pathJoin(context.filesDir.path, currentVolume.name)))
                        deleteVolumeData(currentVolume, parent)
                    }
                } else {
                    dialog.setMessage(R.string.hidden_volume_delete_question)
                    dialog.setPositiveButton(R.string.path_only){ _, _ ->
                        deleteVolumeData(currentVolume, parent)
                    }
                    dialog.setNegativeButton(R.string.whole_volume){ _, _ ->
                        PathUtils.recursiveRemoveDirectory(File(PathUtils.pathJoin(context.filesDir.path, currentVolume.name)))
                        deleteVolumeData(currentVolume, parent)
                    }
                }
            } else {
                if (currentVolume.hash != null) {
                    dialog.setMessage(R.string.delete_hash_or_all)
                    dialog.setNegativeButton(R.string.password_hash_and_path) { _, _ ->
                        deleteVolumeData(currentVolume, parent)
                    }
                    dialog.setPositiveButton(R.string.password_hash) { _, _ ->
                        deletePasswordHash(currentVolume)
                    }
                } else {
                    dialog.setMessage(R.string.ask_delete_volume_path)
                    dialog.setPositiveButton(R.string.ok) {_, _ ->
                        deleteVolumeData(currentVolume, parent)
                    }
                    dialog.setNegativeButton(R.string.cancel, null)
                }
            }
            dialog.show()
        }
        return view
    }

    private fun refresh(parent: ViewGroup) {
        notifyDataSetChanged()
        if (count == 0){
            WidgetUtil.hideWithPadding(parent)
        } else {
            nonScrollableColoredBorderListView.layoutParams.height = nonScrollableColoredBorderListView.computeHeight()
        }
    }
}
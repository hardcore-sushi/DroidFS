package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.NonScrollableColoredBorderListView
import java.util.*

class SavedVolumesAdapter(val context: Context, private val sharedPrefs: SharedPreferences) : BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private lateinit var nonScrollableColoredBorderListView: NonScrollableColoredBorderListView
    private val savedVolumesPaths: MutableList<String> = ArrayList()
    private val sharedPrefsEditor: Editor = sharedPrefs.edit()

    init {
        val savedVolumesPathsSet = sharedPrefs.getStringSet(ConstValues.saved_volumes_key, HashSet()) as Set<String>
        for (volume_path in savedVolumesPathsSet) {
            savedVolumesPaths.add(volume_path)
        }
    }

    private fun updateSharedPrefs() {
        val savedVolumesPathsSet = savedVolumesPaths.toSet()
        sharedPrefsEditor.remove(ConstValues.saved_volumes_key)
        sharedPrefsEditor.putStringSet(ConstValues.saved_volumes_key, savedVolumesPathsSet)
        sharedPrefsEditor.apply()
    }

    override fun getCount(): Int {
        return savedVolumesPaths.size
    }

    override fun getItem(position: Int): String {
        return savedVolumesPaths[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (!::nonScrollableColoredBorderListView.isInitialized){
            nonScrollableColoredBorderListView = parent as NonScrollableColoredBorderListView
        }
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_saved_volume, parent, false)
        val volumeNameTextview = view.findViewById<TextView>(R.id.volume_name_textview)
        val currentVolume = getItem(position)
        volumeNameTextview.text = currentVolume
        val deleteImageview = view.findViewById<ImageView>(R.id.delete_imageview)
        deleteImageview.setOnClickListener {
            val volumePath = savedVolumesPaths[position]
            val dialog = ColoredAlertDialogBuilder(context)
            dialog.setTitle(R.string.warning)
            if (sharedPrefs.getString(volumePath, null) != null){
                dialog.setMessage(context.getString(R.string.delete_hash_or_all))
                dialog.setPositiveButton(context.getString(R.string.delete_all)) { _, _ ->
                    savedVolumesPaths.removeAt(position)
                    sharedPrefsEditor.remove(volumePath)
                    updateSharedPrefs()
                    refresh(parent)
                }
                dialog.setNegativeButton(context.getString(R.string.delete_hash)) { _, _ ->
                    sharedPrefsEditor.remove(volumePath)
                    sharedPrefsEditor.apply()
                }
            } else {
                dialog.setMessage(context.getString(R.string.ask_delete_volume_path))
                dialog.setPositiveButton(R.string.ok) {_, _ ->
                    savedVolumesPaths.removeAt(position)
                    updateSharedPrefs()
                    refresh(parent)
                }
                dialog.setNegativeButton(R.string.cancel, null)
            }
            dialog.show()
        }
        return view
    }

    private fun refresh(parent: ViewGroup) {
        notifyDataSetChanged()
        if (count == 0){
            WidgetUtil.hide(parent)
        } else {
            nonScrollableColoredBorderListView.layoutParams.height = nonScrollableColoredBorderListView.computeHeight()
        }
    }

    fun addVolumePath(volume_path: String) {
        if (!savedVolumesPaths.contains(volume_path)) {
            savedVolumesPaths.add(volume_path)
            updateSharedPrefs()
        }
    }
}
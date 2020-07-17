package sushi.hardcore.droidfs.adapters

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.util.*

class SavedVolumesAdapter(val context: Context, val shared_prefs: SharedPreferences) : BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val saved_volumes_paths: MutableList<String> = ArrayList()
    private val shared_prefs_editor: Editor = shared_prefs.edit()

    init {
        val saved_volumes_paths_set = shared_prefs.getStringSet(ConstValues.saved_volumes_key, HashSet()) as Set<String>
        for (volume_path in saved_volumes_paths_set) {
            saved_volumes_paths.add(volume_path)
        }
    }

    private fun update_shared_prefs() {
        val saved_volumes_paths_set = saved_volumes_paths.toSet()
        shared_prefs_editor.remove(ConstValues.saved_volumes_key)
        shared_prefs_editor.putStringSet(ConstValues.saved_volumes_key, saved_volumes_paths_set)
        shared_prefs_editor.apply()
    }

    override fun getCount(): Int {
        return saved_volumes_paths.size
    }

    override fun getItem(position: Int): String {
        return saved_volumes_paths[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_saved_volume, parent, false)
        val volume_name_textview = view.findViewById<TextView>(R.id.volume_name_textview)
        val currentVolume = getItem(position)
        volume_name_textview.text = currentVolume
        val delete_imageview = view.findViewById<ImageView>(R.id.delete_imageview)
        delete_imageview.setOnClickListener {
            val volume_path = saved_volumes_paths[position]
            val dialog = ColoredAlertDialog(context)
            dialog.setTitle(R.string.warning)
            if (shared_prefs.getString(volume_path, null) != null){
                dialog.setMessage(context.getString(R.string.delete_hash_or_all))
                dialog.setPositiveButton(context.getString(R.string.delete_all)) { _, _ ->
                    saved_volumes_paths.removeAt(position)
                    shared_prefs_editor.remove(volume_path)
                    update_shared_prefs()
                    refresh(parent)
                }
                dialog.setNegativeButton(context.getString(R.string.delete_hash)) { _, _ ->
                    shared_prefs_editor.remove(volume_path)
                    shared_prefs_editor.apply()
                }
            } else {
                dialog.setMessage(context.getString(R.string.ask_delete_volume_path))
                dialog.setPositiveButton(R.string.ok) {_, _ ->
                    saved_volumes_paths.removeAt(position)
                    update_shared_prefs()
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
        }
    }

    fun addVolumePath(volume_path: String) {
        if (!saved_volumes_paths.contains(volume_path)) {
            saved_volumes_paths.add(volume_path)
            update_shared_prefs()
        }
    }
}
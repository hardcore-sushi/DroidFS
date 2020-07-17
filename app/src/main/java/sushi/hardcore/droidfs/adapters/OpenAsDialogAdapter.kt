package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredImageView

class OpenAsDialogAdapter(private val context: Context): BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val items = listOf(
        listOf("image", context.getString(R.string.image), R.drawable.icon_file_image),
        listOf("video", context.getString(R.string.video), R.drawable.icon_file_video),
        listOf("audio", context.getString(R.string.audio), R.drawable.icon_file_audio),
        listOf("text", context.getString(R.string.text), R.drawable.icon_file_text)
    )
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_dialog_listview, parent, false)
        val text = view.findViewById<TextView>(R.id.text)
        text.text = items[position][1] as String
        val icon = view.findViewById<ColoredImageView>(R.id.icon)
        icon.setImageDrawable(context.getDrawable(items[position][2] as Int))
        return view
    }

    override fun getItem(position: Int): String {
        return items[position][0] as String
    }

    override fun getItemId(position: Int): Long { return 0 }

    override fun getCount(): Int { return items.size }
}
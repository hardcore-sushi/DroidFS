package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import sushi.hardcore.droidfs.R

open class IconTextDialogAdapter(private val context: Context): BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    lateinit var items: List<List<Any>>

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_dialog_icon_text, parent, false)
        val text = view.findViewById<TextView>(R.id.text)
        text.text = context.getString(items[position][1] as Int)
        val icon = view.findViewById<ImageView>(R.id.icon)
        icon.setImageDrawable(AppCompatResources.getDrawable(context, items[position][2] as Int))
        return view
    }

    override fun getItem(position: Int): Any {
        return items[position][0] as String
    }

    override fun getItemId(position: Int): Long { return 0 }

    override fun getCount(): Int { return items.size }
}
package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ThemeColor

class DialogSingleChoiceAdapter(private val context: Context, private val entries: List<String>): BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_colored_dialog_single_choice, parent, false)
        val checkedTextView = view.findViewById<CheckedTextView>(android.R.id.text1)
        checkedTextView.text = getItem(position)
        val typedArray = context.theme.obtainStyledAttributes(arrayOf(android.R.attr.listChoiceIndicatorSingle).toIntArray())
        val drawable = typedArray.getDrawable(0)
        typedArray.recycle()
        drawable?.setTint(ThemeColor.getThemeColor(context))
        checkedTextView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
        return view
    }

    override fun getItem(position: Int): String {
        return entries[position]
    }

    override fun getItemId(position: Int): Long { return 0 }

    override fun getCount(): Int { return entries.size }
}
package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.ConstValues.Companion.getAssociatedDrawable
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ThemeColor
import java.text.DateFormat
import java.util.*

class ExplorerElementAdapter(private val context: Context) : BaseAdapter() {
    private val dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, context.resources.configuration.locale)
    private lateinit var explorerElements: List<ExplorerElement>
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    val selectedItems: MutableList<Int> = ArrayList()
    private val themeColor = ThemeColor.getThemeColor(context)
    override fun getCount(): Int {
        return explorerElements.size
    }

    override fun getItem(position: Int): ExplorerElement {
        return explorerElements[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: inflater.inflate(R.layout.adapter_explorer_element, parent, false)
        val currentElement = getItem(position)
        val textElementName = view.findViewById<TextView>(R.id.text_element_name)
        textElementName.text = currentElement.name
        val textElementMtime = view.findViewById<TextView>(R.id.text_element_mtime)
        val textElementSize = view.findViewById<TextView>(R.id.text_element_size)
        textElementSize.text = ""
        var drawableId = R.drawable.icon_folder
        when {
            currentElement.isDirectory -> {
                textElementMtime.text = dateFormat.format(currentElement.mTime)
            }
            currentElement.isParentFolder -> {
                textElementMtime.setText(R.string.parent_folder)
            }
            else -> {
                textElementMtime.text = dateFormat.format(currentElement.mTime)
                textElementSize.text = PathUtils.formatSize(currentElement.size)
                drawableId = getAssociatedDrawable(currentElement.name)
            }
        }
        val elementIcon = view.findViewById<ImageView>(R.id.icon_element)
        val icon = context.getDrawable(drawableId)
        icon?.colorFilter = PorterDuffColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        elementIcon.setImageDrawable(icon)
        if (selectedItems.contains(position)) {
            view.setBackgroundColor(ContextCompat.getColor(context, R.color.item_selected))
        } else {
            view.setBackgroundColor(Color.alpha(0))
        }
        return view
    }

    fun onItemClick(position: Int) {
        if (selectedItems.isNotEmpty()) {
            if (!explorerElements[position].isParentFolder) {
                if (selectedItems.contains(position)) {
                    selectedItems.remove(position)
                } else {
                    selectedItems.add(position)
                }
                notifyDataSetChanged()
            }
        }
    }

    fun onItemLongClick(position: Int) {
        if (!explorerElements[position].isParentFolder) {
            if (!selectedItems.contains(position)) {
                selectedItems.add(position)
            } else {
                selectedItems.remove(position)
            }
            notifyDataSetChanged()
        }
    }

    fun selectAll() {
        for (i in explorerElements.indices) {
            if (!selectedItems.contains(i) && !explorerElements[i].isParentFolder) {
                selectedItems.add(i)
            }
        }
        notifyDataSetChanged()
    }

    fun unSelectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun setExplorerElements(explorer_elements: List<ExplorerElement>) {
        unSelectAll()
        this.explorerElements = explorer_elements
    }
}
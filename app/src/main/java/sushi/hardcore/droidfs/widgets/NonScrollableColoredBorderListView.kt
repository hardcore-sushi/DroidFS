package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ListAdapter
import android.widget.ListView
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.R

class NonScrollableColoredBorderListView: ListView {
    constructor(context: Context) : super(context) { applyColor() }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { applyColor() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { applyColor() }

    fun applyColor() {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
        divider = ColorDrawable(typedValue.data)
        dividerHeight = context.resources.displayMetrics.density.toInt()*2
        background = ContextCompat.getDrawable(context, R.drawable.listview_border)
    }

    fun computeHeight(): Int {
        var totalHeight = 0
        for (i in 0 until adapter.count){
            val item = adapter.getView(i, null, this)
            item.measure(0, 0)
            totalHeight += item.measuredHeight
        }
        return totalHeight + (dividerHeight * (adapter.count-1))
    }

    override fun setAdapter(adapter: ListAdapter?) {
        super.setAdapter(adapter)
        layoutParams.height = computeHeight()
    }
}
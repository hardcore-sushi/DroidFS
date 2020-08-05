package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.ListAdapter

class NonScrollableColoredBorderListView: ColoredBorderListView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)

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
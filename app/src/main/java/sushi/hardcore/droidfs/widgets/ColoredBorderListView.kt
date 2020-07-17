package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.Log
import android.widget.ListView
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.R

class ColoredBorderListView: ListView {
    constructor(context: Context) : super(context) {
        applyColor()
    }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs){
        applyColor()
    }
    private fun applyColor(){
        val background = ContextCompat.getDrawable(context, R.drawable.listview_border) as StateListDrawable
        val dcs = background.constantState as DrawableContainer.DrawableContainerState
        val drawableItems = dcs.children
        val gradientDrawable = drawableItems[0] as GradientDrawable
        val themeColor = ThemeColor.getThemeColor(context)
        gradientDrawable.setStroke(context.resources.displayMetrics.density.toInt()*2, themeColor)
        super.setBackground(background)
        super.setDivider(ColorDrawable(themeColor))
        super.setDividerHeight(context.resources.displayMetrics.density.toInt()*2)
    }
}
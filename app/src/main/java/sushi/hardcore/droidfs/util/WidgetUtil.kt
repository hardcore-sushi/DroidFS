package sushi.hardcore.droidfs.util

import android.view.View
import android.widget.LinearLayout

object WidgetUtil {
    fun hideWithPadding(view: View){
        view.visibility = View.INVISIBLE
        view.setPadding(0, 0, 0, 0)
        view.layoutParams = LinearLayout.LayoutParams(0, 0)
    }
    fun hide(view: View){
        view.visibility = View.INVISIBLE
        view.layoutParams = LinearLayout.LayoutParams(0, 0)
    }
    fun show(view: View, layoutParams: LinearLayout.LayoutParams){
        view.visibility = View.VISIBLE
        view.layoutParams = layoutParams
    }
}
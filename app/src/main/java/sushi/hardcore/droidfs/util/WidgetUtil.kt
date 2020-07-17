package sushi.hardcore.droidfs.util

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

object WidgetUtil {
    fun hide(view: View){
        view.visibility = View.INVISIBLE
        view.setPadding(0, 0, 0, 0)
        view.layoutParams = LinearLayout.LayoutParams(0, 0)
    }
}
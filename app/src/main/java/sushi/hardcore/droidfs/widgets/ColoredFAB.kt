package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ColoredFAB: FloatingActionButton {
    constructor(context: Context) : super(context) { applyColor() }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { applyColor() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { applyColor() }
    private fun applyColor(){
        val themeColor = ThemeColor.getThemeColor(context)
        backgroundTintList = ColorStateList.valueOf(themeColor)
        setColorFilter(ContextCompat.getColor(context, android.R.color.white))
    }
}
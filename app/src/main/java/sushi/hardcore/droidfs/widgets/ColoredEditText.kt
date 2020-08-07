package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class ColoredEditText: AppCompatEditText {
    constructor(context: Context) : super(context) { applyColor() }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { applyColor() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { applyColor() }
    private fun applyColor(){
        super.setBackgroundTintList(ColorStateList.valueOf(ThemeColor.getThemeColor(context)))
    }
}

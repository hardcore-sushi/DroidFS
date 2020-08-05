package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton

class ColoredImageButton: AppCompatImageButton {
    constructor(context: Context) : super(context) { applyColor() }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { applyColor() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { applyColor() }
    private fun applyColor(){
        super.setColorFilter(ThemeColor.getThemeColor(context))
    }
}
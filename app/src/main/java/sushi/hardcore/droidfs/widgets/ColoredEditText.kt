package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.R

class ColoredEditText: AppCompatEditText {
    constructor(context: Context) : super(context) { applyColor() }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { applyColor() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { applyColor() }
    private fun applyColor(){
        super.setBackgroundTintList(ColorStateList.valueOf(ThemeColor.getThemeColor(context)))
    }
}

package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar

class ColoredSeekBar : AppCompatSeekBar {
    constructor(context: Context) : super(context) {
        applyColor()
    }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs){
        applyColor()
    }
    private fun applyColor(){
        val colorFilter = PorterDuffColorFilter(ThemeColor.getThemeColor(context), PorterDuff.Mode.SRC_IN)
        super.getProgressDrawable().colorFilter = colorFilter
        super.getThumb().colorFilter = colorFilter
    }
}
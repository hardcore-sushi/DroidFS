package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

class ColoredButton: AppCompatButton {
    constructor(context: Context) : super(context) {
        applyColor()
    }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs){
        applyColor()
    }
    private fun applyColor(){
        super.setBackgroundTintList(ColorStateList.valueOf(ThemeColor.getThemeColor(context)))
    }
}
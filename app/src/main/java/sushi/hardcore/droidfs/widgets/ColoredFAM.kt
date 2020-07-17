package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import com.github.clans.fab.FloatingActionMenu

class ColoredFAM: FloatingActionMenu {
    constructor(context: Context) : super(context) {
        applyColor()
    }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs){
        applyColor()
    }
    private fun applyColor(){
        val themeColor = ThemeColor.getThemeColor(context)
        super.setMenuButtonColorNormal(themeColor)
        super.setMenuButtonColorPressed(themeColor)
    }
}
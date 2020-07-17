package sushi.hardcore.droidfs

import android.widget.ImageView

class ColorEngine(val themeColor: Int) {
    fun applyTo(imageView: ImageView){
        imageView.setColorFilter(themeColor)
    }
}
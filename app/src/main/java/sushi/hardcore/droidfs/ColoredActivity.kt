package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import sushi.hardcore.droidfs.widgets.ThemeColor

open class ColoredActivity: CyaneaAppCompatActivity() {
    protected lateinit var sharedPrefs: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeColor = ThemeColor.getThemeColor(this)
        if (cyanea.accent != themeColor){
            val backgroundColor = ContextCompat.getColor(this, R.color.backgroundColor)
            cyanea.edit{
                accent(themeColor)
                //accentDark(themeColor)
                //accentLight(themeColor)
                background(backgroundColor)
                //backgroundDark(backgroundColor)
                //backgroundLight(backgroundColor)
            }
        }
    }
}
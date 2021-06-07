package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import sushi.hardcore.droidfs.widgets.ThemeColor

open class BaseActivity: CyaneaAppCompatActivity() {
    protected lateinit var sharedPrefs: SharedPreferences
    protected var isRecreating = false
    override fun onCreate(savedInstanceState: Bundle?) {
        val themeColor = ThemeColor.getThemeColor(this)
        if (cyanea.accent != themeColor){
            changeThemeColor(themeColor)
        }
        super.onCreate(savedInstanceState)
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sharedPrefs.getBoolean("usf_screenshot", false)){
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    fun changeThemeColor(themeColor: Int? = null){
        val accentColor = themeColor ?: ThemeColor.getThemeColor(this)
        val backgroundColor = ContextCompat.getColor(this, R.color.backgroundColor)
        isRecreating = true
        cyanea.edit{
            accent(accentColor)
            //accentDark(themeColor)
            //accentLight(themeColor)
            background(backgroundColor)
            //backgroundDark(backgroundColor)
            //backgroundLight(backgroundColor)
        }.recreate(this)
    }
}
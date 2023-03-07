package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity: AppCompatActivity() {
    protected lateinit var sharedPrefs: SharedPreferences
    protected var applyCustomTheme: Boolean = true
    lateinit var themeValue: String
    private var shouldCheckTheme = true

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPrefs = (application as VolumeManagerApp).sharedPreferences
        themeValue = sharedPrefs.getString(Constants.THEME_VALUE_KEY, Constants.DEFAULT_THEME_VALUE)!!
        if (shouldCheckTheme && applyCustomTheme) {
            when (themeValue) {
                "black_green" -> setTheme(R.style.BlackGreen)
                "dark_red" -> setTheme(R.style.DarkRed)
                "black_red" -> setTheme(R.style.BlackRed)
                "dark_blue" -> setTheme(R.style.DarkBlue)
                "black_blue" -> setTheme(R.style.BlackBlue)
                "dark_yellow" -> setTheme(R.style.DarkYellow)
                "black_yellow" -> setTheme(R.style.BlackYellow)
                "dark_orange" -> setTheme(R.style.DarkOrange)
                "black_orange" -> setTheme(R.style.BlackOrange)
                "dark_purple" -> setTheme(R.style.DarkPurple)
                "black_purple" -> setTheme(R.style.BlackPurple)
                "dark_pink" -> setTheme(R.style.DarkPink)
                "black_pink" -> setTheme(R.style.BlackPink)
            }
        }
        super.onCreate(savedInstanceState)
        if (!sharedPrefs.getBoolean("usf_screenshot", false)){
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // must not be called if applyCustomTheme is false
    fun onThemeChanged(newThemeValue: String) {
        if (newThemeValue != themeValue) {
            themeValue = newThemeValue
            shouldCheckTheme = false
            recreate()
        }
    }
}
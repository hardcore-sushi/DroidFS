package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

open class BaseActivity: AppCompatActivity() {
    protected lateinit var sharedPrefs: SharedPreferences
    lateinit var themeValue: String
    private var shouldCheckTheme = true

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (shouldCheckTheme) {
            themeValue = sharedPrefs.getString("theme", ConstValues.DEFAULT_THEME_VALUE)!!
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
            }
        } else {
            shouldCheckTheme = true
        }
        super.onCreate(savedInstanceState)
        if (!sharedPrefs.getBoolean("usf_screenshot", false)){
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStart() {
        super.onStart()
        val newThemeValue = sharedPrefs.getString("theme", "dark_green")!!
        onThemeChanged(newThemeValue)
    }

    fun onThemeChanged(newThemeValue: String) {
        if (newThemeValue != themeValue) {
            themeValue = newThemeValue
            shouldCheckTheme = false
            recreate()
        }
    }
}
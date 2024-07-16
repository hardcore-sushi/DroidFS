package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

open class BaseActivity: AppCompatActivity() {
    protected lateinit var sharedPrefs: SharedPreferences
    protected var applyCustomTheme: Boolean = true
    lateinit var theme: Theme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        theme = Theme.fromSharedPrefs(sharedPrefs)
        if (applyCustomTheme) {
            setTheme(theme.toResourceId())
        }
        if (!sharedPrefs.getBoolean("usf_screenshot", false)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
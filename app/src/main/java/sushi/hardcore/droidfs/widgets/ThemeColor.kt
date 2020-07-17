package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.R

object ThemeColor {
    fun getThemeColor(context: Context): Int {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPrefs.getInt("themeColor", ContextCompat.getColor(context, R.color.themeColor))
    }
}

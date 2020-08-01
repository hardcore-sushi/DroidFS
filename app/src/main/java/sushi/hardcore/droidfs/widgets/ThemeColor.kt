package sushi.hardcore.droidfs.widgets

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.R

object ThemeColor {
    fun getThemeColor(context: Context): Int {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPrefs.getInt("themeColor", ContextCompat.getColor(context, R.color.themeColor))
    }
    fun tintPreferenceIcons(preference: Preference, color: Int){
        if (preference is PreferenceGroup) {
            for (i in 0 until preference.preferenceCount) {
                tintPreferenceIcons(preference.getPreference(i), color)
            }
        } else if (preference.icon != null) {
            DrawableCompat.setTint(preference.icon, color)
        }
    }
}

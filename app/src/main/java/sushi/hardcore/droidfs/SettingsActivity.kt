package sushi.hardcore.droidfs

import android.os.Bundle
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import sushi.hardcore.droidfs.databinding.ActivitySettingsBinding
import sushi.hardcore.droidfs.widgets.SimpleActionPreference
import sushi.hardcore.droidfs.widgets.ThemeColor

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val screen = intent.extras?.getString("screen") ?: "main"
        val fragment = if (screen == "UnsafeFeaturesSettingsFragment") {
            UnsafeFeaturesSettingsFragment()
        } else {
            MainSettingsFragment()
        }
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId){
            android.R.id.home -> {
                onBackPressed() //return to the previous fragment rather than the activity
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class MainSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            ThemeColor.tintPreferenceIcons(preferenceScreen, ThemeColor.getThemeColor(requireContext()))
            var originalThemeColor: Int? = null
            context?.let {
                originalThemeColor = ContextCompat.getColor(it, R.color.themeColor)
            }
            findPreference<ColorPreferenceCompat>("themeColor")?.let { colorPicker ->
                colorPicker.onPreferenceChangeListener = Preference.OnPreferenceChangeListener{ _, _ ->
                    (activity as SettingsActivity).changeThemeColor()
                    true
                }
                findPreference<SimpleActionPreference>("resetThemeColor")?.onClick = {
                    originalThemeColor?.let {
                        colorPicker.saveValue(it)
                        val settingsActivity = (activity as SettingsActivity)
                        Thread {
                            settingsActivity.sharedPrefs.edit().commit()
                            settingsActivity.runOnUiThread { settingsActivity.changeThemeColor() }
                        }.start()
                    }
                }
            }
        }
    }

    class UnsafeFeaturesSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.unsafe_features_preferences, rootKey)
            ThemeColor.tintPreferenceIcons(preferenceScreen, ThemeColor.getThemeColor(requireContext()))
        }
    }
}
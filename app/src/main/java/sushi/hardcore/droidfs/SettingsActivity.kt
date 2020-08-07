package sushi.hardcore.droidfs

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.widgets.SimpleActionPreference
import sushi.hardcore.droidfs.widgets.ThemeColor


class SettingsActivity : BaseActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val screen = intent.extras?.getString("screen") ?: "main"
        val fragment: Fragment
        fragment = if (screen == "UnsafeFeaturesSettingsFragment") {
            UnsafeFeaturesSettingsFragment()
        } else {
            SettingsFragment()
        }
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
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

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)
        fragment.arguments = pref.extras
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.beginTransaction().replace(R.id.settings, fragment).addToBackStack(null).commit()
        return true
    }
}
package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.Toast
import androidx.preference.*
import sushi.hardcore.droidfs.databinding.ActivitySettingsBinding
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
                onBackPressedDispatcher.onBackPressed() //return to the previous fragment rather than the activity
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class MainSettingsFragment : PreferenceFragmentCompat() {
        private lateinit var sharedPrefs: SharedPreferences
        private lateinit var maxSizePreference: Preference

        private fun setThumbnailMaxSize(input: String) {
            val value: Long
            try {
                value = input.toLong()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show()
                showMaxSizeDialog()
                return
            }
            val size = value*1000
            if (size < 0) {
                Toast.makeText(requireContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show()
                showMaxSizeDialog()
            } else {
                with(sharedPrefs.edit()) {
                    putLong(ConstValues.THUMBNAIL_MAX_SIZE_KEY, value)
                    apply()
                }
                maxSizePreference.summary = PathUtils.formatSize(size)
            }
        }

        private fun showMaxSizeDialog() {
            with (EditTextDialog((requireActivity() as BaseActivity), R.string.thumbnail_max_size) {
                setThumbnailMaxSize(it)
            }) {
                with (binding.dialogEditText) {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    hint = getString(R.string.size_hint)
                }
                show()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
                (activity as BaseActivity).onThemeChanged(newValue as String)
                true
            }
            findPreference<Preference>(ConstValues.THUMBNAIL_MAX_SIZE_KEY)?.let {
                maxSizePreference = it
                maxSizePreference.summary = getString(
                    R.string.thumbnail_max_size_summary,
                    PathUtils.formatSize(sharedPrefs.getLong(
                        ConstValues.THUMBNAIL_MAX_SIZE_KEY, ConstValues.DEFAULT_THUMBNAIL_MAX_SIZE
                    )*1000)
                )
                maxSizePreference.setOnPreferenceClickListener {
                    showMaxSizeDialog()
                    false
                }
            }
        }
    }

    class UnsafeFeaturesSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.unsafe_features_preferences, rootKey)
            findPreference<SwitchPreference>("usf_fingerprint")?.setOnPreferenceChangeListener { _, checked ->
                if (checked as Boolean) {
                    var errorMsg: String? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val reason = when (FingerprintProtector.canAuthenticate(requireContext())) {
                            0 -> null
                            1 -> R.string.keyguard_not_secure
                            2 -> R.string.no_hardware
                            3 -> R.string.hardware_unavailable
                            4 -> R.string.no_fingerprint
                            else -> R.string.unknown_error
                        }
                        reason?.let {
                            errorMsg = getString(R.string.fingerprint_error_msg, getString(it))
                        }
                    } else {
                        errorMsg = getString(R.string.error_marshmallow_required)
                    }
                    if (errorMsg == null) {
                        true
                    } else {
                        CustomAlertDialogBuilder(requireContext(), (requireActivity() as BaseActivity).themeValue)
                            .setTitle(R.string.error)
                            .setMessage(errorMsg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                        false
                    }
                } else {
                    true
                }
            }
        }
    }
}
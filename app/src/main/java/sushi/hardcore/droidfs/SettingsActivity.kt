package sushi.hardcore.droidfs

import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import sushi.hardcore.droidfs.content_providers.VolumeProvider
import sushi.hardcore.droidfs.databinding.ActivitySettingsBinding
import sushi.hardcore.droidfs.util.AndroidUtils
import sushi.hardcore.droidfs.util.Compat
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog

class SettingsActivity : BaseActivity() {
    private val notificationPermissionHelper = AndroidUtils.NotificationPermissionHelper(this)

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
        private lateinit var columnCountPreference: Preference

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
                    putLong(Constants.THUMBNAIL_MAX_SIZE_KEY, value)
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

        private fun setGridColumnCount(input: String) {
            val value: Int
            try {
                value = input.toInt()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show()
                showGridColumnCountDialog()
                return
            }
            if (value < 0) {
                Toast.makeText(requireContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show()
                showGridColumnCountDialog()
            } else {
                with(sharedPrefs.edit()) {
                    putInt(Constants.GRID_COLUMN_COUNT_KEY, value)
                    apply()
                }
                columnCountPreference.summary = input
            }
        }

        private fun showGridColumnCountDialog() {
            with (EditTextDialog((requireActivity() as BaseActivity), R.string.grid_column_count) {
                setGridColumnCount(it)
            }) {
                with (binding.dialogEditText) {
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                show()
            }
        }

        private fun refreshTheme() {
            with(requireActivity()) {
                startActivity(
                    Intent(this, SettingsActivity::class.java),
                    ActivityOptions.makeCustomAnimation(
                        this,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    ).toBundle()
                )
                finish()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            findPreference<ListPreference>("color")?.setOnPreferenceChangeListener { _, _ ->
                refreshTheme()
                true
            }
            findPreference<SwitchPreferenceCompat>("black_theme")?.setOnPreferenceChangeListener { _, _ ->
                refreshTheme()
                true
            }
            findPreference<Preference>(Constants.THUMBNAIL_MAX_SIZE_KEY)?.let {
                maxSizePreference = it
                maxSizePreference.summary = getString(
                    R.string.thumbnail_max_size_summary,
                    PathUtils.formatSize(sharedPrefs.getLong(
                        Constants.THUMBNAIL_MAX_SIZE_KEY, Constants.DEFAULT_THUMBNAIL_MAX_SIZE
                    )*1000)
                )
                maxSizePreference.setOnPreferenceClickListener {
                    showMaxSizeDialog()
                    false
                }
            }
            findPreference<Preference>(Constants.GRID_COLUMN_COUNT_KEY)?.let {
                columnCountPreference = it
                columnCountPreference.summary = getString(
                    R.string.grid_column_count_summary,
                    sharedPrefs.getInt(Constants.GRID_COLUMN_COUNT_KEY, Constants.DEFAULT_GRID_COLUMN_COUNT)
                )
                columnCountPreference.setOnPreferenceClickListener {
                    showGridColumnCountDialog()
                    false
                }
            }
            findPreference<Preference>("logcat")?.setOnPreferenceClickListener { _ ->
                startActivity(Intent(requireContext(), LogcatActivity::class.java))
                true
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
                        CustomAlertDialogBuilder(requireContext(), (requireActivity() as BaseActivity).theme)
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
            val switchBackground = findPreference<SwitchPreference>("usf_background")!!
            val switchKeepOpen = findPreference<SwitchPreference>("usf_keep_open")!!
            val switchExternalOpen = findPreference<SwitchPreference>("usf_open")!!
            val switchExpose = findPreference<SwitchPreference>("usf_expose")!!
            val switchSafWrite = findPreference<SwitchPreference>("usf_saf_write")!!

            fun onUsfBackgroundChanged(usfBackground: Boolean) {
                fun updateSwitchPreference(switch: SwitchPreference) = with (switch) {
                    isChecked = isChecked && usfBackground
                    isEnabled = usfBackground
                    onPreferenceChangeListener?.onPreferenceChange(switch, isChecked)
                }
                updateSwitchPreference(switchKeepOpen)
                updateSwitchPreference(switchExpose)
            }
            onUsfBackgroundChanged(switchBackground.isChecked)

            fun updateSafWrite(usfOpen: Boolean? = null, usfExpose: Boolean? = null) {
                switchSafWrite.isEnabled = usfOpen ?: switchExternalOpen.isChecked || usfExpose ?: switchExpose.isChecked
            }
            updateSafWrite()

            switchBackground.setOnPreferenceChangeListener { _, checked ->
                onUsfBackgroundChanged(checked as Boolean)
                true
            }
            switchExternalOpen.setOnPreferenceChangeListener { _, checked ->
                updateSafWrite(usfOpen = checked as Boolean)
                true
            }
            switchExpose.setOnPreferenceChangeListener { _, checked ->
                updateSafWrite(usfExpose = checked as Boolean)
                VolumeProvider.notifyRootsChanged(requireContext())
                true
            }

            switchKeepOpen.setOnPreferenceChangeListener { _, checked ->
                if (checked as Boolean) {
                    (requireActivity() as SettingsActivity).notificationPermissionHelper.askAndRun {
                        requireContext().let {
                            if (AndroidUtils.isServiceRunning(it, KeepAliveService::class.java)) {
                                ContextCompat.startForegroundService(it, Intent(it, KeepAliveService::class.java).apply {
                                    action = KeepAliveService.ACTION_FOREGROUND
                                })
                            }
                        }
                    }
                }
                true
            }
            findPreference<ListPreference>("export_method")!!.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as String == "memory" && !Compat.isMemFileSupported()) {
                    CustomAlertDialogBuilder(requireContext(), (requireActivity() as BaseActivity).theme)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.memfd_create_unsupported, Compat.MEMFD_CREATE_MINIMUM_KERNEL_VERSION))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                    return@setOnPreferenceChangeListener false
                }
                EncryptedFileProvider.exportMethod = EncryptedFileProvider.ExportMethod.parse(newValue)
                true
            }
        }
    }
}

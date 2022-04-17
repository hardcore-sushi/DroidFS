package sushi.hardcore.droidfs.add_volume

import android.os.Bundle
import android.view.MenuItem
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityAddVolumeBinding

class AddVolumeActivity: BaseActivity() {

    companion object {
        const val RESULT_VOLUME_ADDED = 1
        const val RESULT_HASH_STORAGE_RESET = 2
    }

    private lateinit var binding: ActivityAddVolumeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddVolumeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(
                    R.id.fragment_container,
                    SelectPathFragment.newInstance(themeValue),
                )
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0)
                supportFragmentManager.popBackStack()
            else
                finish()
        }
        return super.onOptionsItemSelected(item)
    }

    fun onFragmentLoaded(selectPathFragment: Boolean) {
        title = getString(
            if (selectPathFragment) {
                R.string.add_volume
            } else {
                R.string.create_volume
            }
        )
    }

    fun onSelectedAlreadySavedVolume() {
        finish()
    }

    fun onVolumeAdded(hashStorageReset: Boolean) {
        setResult(if (hashStorageReset) RESULT_HASH_STORAGE_RESET else RESULT_VOLUME_ADDED)
        finish()
    }

    fun createVolume(volumePath: String, isHidden: Boolean) {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragment_container, CreateVolumeFragment.newInstance(
                    themeValue,
                    volumePath,
                    isHidden,
                    sharedPrefs.getBoolean(ConstValues.PIN_PASSWORDS_KEY, false),
                    sharedPrefs.getBoolean("usf_fingerprint", false),
                )
            )
            .addToBackStack(null)
            .commit()
    }
}
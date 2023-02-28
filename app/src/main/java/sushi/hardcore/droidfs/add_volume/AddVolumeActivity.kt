package sushi.hardcore.droidfs.add_volume

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.addCallback
import sushi.hardcore.droidfs.*
import sushi.hardcore.droidfs.databinding.ActivityAddVolumeBinding
import sushi.hardcore.droidfs.explorers.ExplorerRouter

class AddVolumeActivity: BaseActivity() {

    companion object {
        const val RESULT_USER_BACK = 10
    }

    private lateinit var binding: ActivityAddVolumeBinding
    private lateinit var explorerRouter: ExplorerRouter
    private lateinit var volumeOpener: VolumeOpener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddVolumeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        explorerRouter = ExplorerRouter(this, intent)
        volumeOpener = VolumeOpener(this)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(
                    R.id.fragment_container,
                    SelectPathFragment.newInstance(theme, explorerRouter.pickMode),
                )
                .commit()
        }
        onBackPressedDispatcher.addCallback(this) {
            setResult(RESULT_USER_BACK)
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0)
                supportFragmentManager.popBackStack()
            else {
                setResult(RESULT_USER_BACK)
                finish()
            }
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

    fun startExplorer(volumeId: Int, volumeShortName: String) {
        startActivity(explorerRouter.getExplorerIntent(volumeId, volumeShortName))
        finish()
    }

    fun onVolumeSelected(volume: VolumeData, rememberVolume: Boolean) {
        if (rememberVolume) {
            setResult(RESULT_USER_BACK)
            finish()
        } else {
            volumeOpener.openVolume(volume, false, object : VolumeOpener.VolumeOpenerCallbacks {
                override fun onVolumeOpened(id: Int) {
                    startExplorer(id, volume.shortName)
                }
            })
        }
    }

    fun createVolume(volumePath: String, isHidden: Boolean, rememberVolume: Boolean) {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragment_container, CreateVolumeFragment.newInstance(
                    theme,
                    volumePath,
                    isHidden,
                    rememberVolume,
                    sharedPrefs.getBoolean(Constants.PIN_PASSWORDS_KEY, false),
                    sharedPrefs.getBoolean("usf_fingerprint", false),
                )
            )
            .addToBackStack(null)
            .commit()
    }
}
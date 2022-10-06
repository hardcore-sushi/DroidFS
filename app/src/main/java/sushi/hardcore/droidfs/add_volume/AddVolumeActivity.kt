package sushi.hardcore.droidfs.add_volume

import android.os.Bundle
import android.view.MenuItem
import sushi.hardcore.droidfs.*
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.databinding.ActivityAddVolumeBinding
import sushi.hardcore.droidfs.explorers.ExplorerRouter
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.IntentUtils

class AddVolumeActivity: BaseActivity() {

    companion object {
        const val RESULT_USER_BACK = 10
    }

    private lateinit var binding: ActivityAddVolumeBinding
    private lateinit var explorerRouter: ExplorerRouter
    private lateinit var volumeOpener: VolumeOpener
    private var usfKeepOpen = false
    var shouldCloseVolume = true // used when launched to pick file from another volume

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddVolumeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        usfKeepOpen = sharedPrefs.getBoolean("usf_keep_open", false)
        explorerRouter = ExplorerRouter(this, intent)
        volumeOpener = VolumeOpener(this)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(
                    R.id.fragment_container,
                    SelectPathFragment.newInstance(themeValue, explorerRouter.pickMode),
                )
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0)
                supportFragmentManager.popBackStack()
            else {
                setResult(RESULT_USER_BACK)
                shouldCloseVolume = false
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        setResult(RESULT_USER_BACK)
        shouldCloseVolume = false
        super.onBackPressed()
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

    fun startExplorer(encryptedVolume: EncryptedVolume, volumeShortName: String) {
        startActivity(explorerRouter.getExplorerIntent(encryptedVolume, volumeShortName))
        shouldCloseVolume = false
        finish()
    }

    fun onVolumeSelected(volume: VolumeData, rememberVolume: Boolean) {
        if (rememberVolume) {
            setResult(RESULT_USER_BACK)
            shouldCloseVolume = false
            finish()
        } else {
            volumeOpener.openVolume(volume, false, object : VolumeOpener.VolumeOpenerCallbacks {
                override fun onVolumeOpened(encryptedVolume: EncryptedVolume, volumeShortName: String) {
                    startExplorer(encryptedVolume, volumeShortName)
                }
            })
        }
    }

    fun createVolume(volumePath: String, isHidden: Boolean, rememberVolume: Boolean) {
        supportFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragment_container, CreateVolumeFragment.newInstance(
                    themeValue,
                    volumePath,
                    isHidden,
                    rememberVolume,
                    sharedPrefs.getBoolean(ConstValues.PIN_PASSWORDS_KEY, false),
                    sharedPrefs.getBoolean("usf_fingerprint", false),
                )
            )
            .addToBackStack(null)
            .commit()
    }

    override fun onStart() {
        super.onStart()
        shouldCloseVolume = true
    }

    override fun onStop() {
        super.onStop()
        if (explorerRouter.pickMode && !usfKeepOpen && shouldCloseVolume) {
            IntentUtils.getParcelableExtra<EncryptedVolume>(intent, "volume")?.close()
            RestrictedFileProvider.wipeAll(this)
            finish()
        }
    }
}
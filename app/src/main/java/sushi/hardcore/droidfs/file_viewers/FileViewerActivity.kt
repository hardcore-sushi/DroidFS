package sushi.hardcore.droidfs.file_viewers

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.IntentUtils
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder

abstract class FileViewerActivity: BaseActivity() {
    protected lateinit var encryptedVolume: EncryptedVolume
    protected lateinit var filePath: String
    private lateinit var originalParentPath: String
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private var windowTypeMask = 0
    private var isFinishingIntentionally = false
    private var usf_keep_open = false
    private var foldersFirst = true
    private var wasMapped = false
    protected val mappedPlaylist = mutableListOf<ExplorerElement>()
    protected var currentPlaylistIndex = -1
    protected open var fullscreenMode = true
    private val legacyMod by lazy {
        sharedPrefs.getBoolean("legacyMod", false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = intent.getStringExtra("path")!!
        originalParentPath = PathUtils.getParentPath(filePath)
        encryptedVolume = IntentUtils.getParcelableExtra(intent, "volume")!!
        usf_keep_open = sharedPrefs.getBoolean("usf_keep_open", false)
        foldersFirst = sharedPrefs.getBoolean("folders_first", true)
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.addOnControllableInsetsChangedListener { _, typeMask ->
            windowTypeMask = typeMask
        }
        onBackPressedDispatcher.addCallback(this) {
            isFinishingIntentionally = true
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
        if (fullscreenMode) {
            fixNavBarColor()
            hideSystemUi()
        }
        viewFile()
    }

    private fun fixNavBarColor() {
        window.navigationBarColor = ContextCompat.getColor(this, R.color.fullScreenBackgroundColor)
    }

    private fun hideSystemUi() {
        if (legacyMod) {
            @Suppress("Deprecation")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    abstract fun getFileType(): String
    abstract fun viewFile()

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (fullscreenMode && windowTypeMask and WindowInsetsCompat.Type.statusBars() == 0) {
            hideSystemUi()
        }
    }

    protected fun loadWholeFile(path: String, fileSize: Long? = null, callback: (ByteArray) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = encryptedVolume.loadWholeFile(path, size = fileSize)
            if (isActive) {
                withContext(Dispatchers.Main) {
                    if (result.second == 0) {
                        callback(result.first!!)
                    } else {
                        val dialog = CustomAlertDialogBuilder(this@FileViewerActivity, themeValue)
                            .setTitle(R.string.error)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok) { _, _ -> goBackToExplorer() }
                        when (result.second) {
                            1 -> dialog.setMessage(R.string.get_size_failed)
                            2 -> dialog.setMessage(R.string.outofmemoryerror_msg)
                            3 -> dialog.setMessage(R.string.read_file_failed)
                            4 -> dialog.setMessage(R.string.io_error)
                        }
                        dialog.show()
                    }
                }
            }
        }
    }

    protected fun createPlaylist() {
        if (!wasMapped){
            encryptedVolume.recursiveMapFiles(originalParentPath)?.let { elements ->
                for (e in elements) {
                    if (e.isRegularFile) {
                        if (ConstValues.isExtensionType(getFileType(), e.name) || filePath == e.fullPath) {
                            mappedPlaylist.add(e)
                        }
                    }
                }
            }
            val sortOrder = intent.getStringExtra("sortOrder") ?: "name"
            ExplorerElement.sortBy(sortOrder, foldersFirst, mappedPlaylist)
            //find current index
            for ((i, e) in mappedPlaylist.withIndex()){
                if (filePath == e.fullPath){
                    currentPlaylistIndex = i
                    break
                }
            }
            wasMapped = true
        }
    }

    protected fun playlistNext(forward: Boolean) {
        createPlaylist()
        currentPlaylistIndex = if (forward) {
            (currentPlaylistIndex+1)%mappedPlaylist.size
        } else {
            var x = (currentPlaylistIndex-1)%mappedPlaylist.size
            if (x < 0) {
                x += mappedPlaylist.size
            }
            x
        }
        filePath = mappedPlaylist[currentPlaylistIndex].fullPath
    }

    protected fun refreshPlaylist() {
        mappedPlaylist.clear()
        wasMapped = false
        createPlaylist()
    }

    protected fun goBackToExplorer() {
        isFinishingIntentionally = true
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isFinishingIntentionally) {
            encryptedVolume.close()
            RestrictedFileProvider.wipeAll(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!usf_keep_open) {
            finish()
        }
    }
}

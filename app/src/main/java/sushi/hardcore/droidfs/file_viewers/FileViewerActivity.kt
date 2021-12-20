package sushi.hardcore.droidfs.file_viewers

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.GocryptfsVolume
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder

abstract class FileViewerActivity: BaseActivity() {
    protected lateinit var gocryptfsVolume: GocryptfsVolume
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
    private val legacyMod by lazy {
        sharedPrefs.getBoolean("legacyMod", false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = intent.getStringExtra("path")!!
        originalParentPath = PathUtils.getParentPath(filePath)
        val sessionID = intent.getIntExtra("sessionID", -1)
        gocryptfsVolume = GocryptfsVolume(applicationContext, sessionID)
        usf_keep_open = sharedPrefs.getBoolean("usf_keep_open", false)
        foldersFirst = sharedPrefs.getBoolean("folders_first", true)
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.addOnControllableInsetsChangedListener { _, typeMask ->
            windowTypeMask = typeMask
        }
        hideSystemUi()
        viewFile()
    }

    open fun hideSystemUi() {
        if (legacyMod) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    abstract fun getFileType(): String
    abstract fun viewFile()

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (windowTypeMask and WindowInsetsCompat.Type.statusBars() == 0) {
            hideSystemUi()
        }
    }

    protected fun loadWholeFile(path: String): ByteArray? {
        val result = gocryptfsVolume.loadWholeFile(path)
        if (result.second != 0) {
            val dialog = CustomAlertDialogBuilder(this, themeValue)
                .setTitle(R.string.error)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> goBackToExplorer() }
            when (result.second) {
                1 -> dialog.setMessage(R.string.get_size_failed)
                2 -> dialog.setMessage(R.string.outofmemoryerror_msg)
                else -> dialog.setMessage(R.string.read_file_failed)
            }
            dialog.show()
        }
        return result.first
    }

    protected fun createPlaylist() {
        if (!wasMapped){
            for (e in gocryptfsVolume.recursiveMapFiles(originalParentPath)) {
                if (e.isRegularFile) {
                    if (ConstValues.isExtensionType(getFileType(), e.name) || filePath == e.fullPath) {
                        mappedPlaylist.add(e)
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
            gocryptfsVolume.close()
            RestrictedFileProvider.wipeAll(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!usf_keep_open) {
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isFinishingIntentionally = true
    }
}

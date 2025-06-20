package sushi.hardcore.droidfs.file_viewers

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.FileTypes
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.finishOnClose
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder

abstract class FileViewerActivity: BaseActivity() {

    class FileViewerViewModel : ViewModel() {
        val playlist = mutableListOf<ExplorerElement>()
        var currentPlaylistIndex = -1
        var filePath: String? = null
    }

    protected lateinit var encryptedVolume: EncryptedVolume
    private lateinit var originalParentPath: String
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private var windowTypeMask = 0
    private val playlistMutex = Mutex()
    protected val fileViewerViewModel: FileViewerViewModel by viewModels()
    private val isLegacyFullscreen = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (fileViewerViewModel.filePath == null) {
            fileViewerViewModel.filePath = intent.getStringExtra("path")!!
        }
        originalParentPath = PathUtils.getParentPath(fileViewerViewModel.filePath!!)
        encryptedVolume = (application as VolumeManagerApp).volumeManager.getVolume(
            intent.getIntExtra("volumeId", -1)
        )!!
        finishOnClose(encryptedVolume)
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.addOnControllableInsetsChangedListener { _, typeMask ->
            windowTypeMask = typeMask
        }
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        viewFile()
    }

    open fun showPartialSystemUi() {
        if (isLegacyFullscreen) {
            @Suppress("Deprecation")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
            windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    open fun hideSystemUi() {
        if (isLegacyFullscreen) {
            @Suppress("Deprecation")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            }
        }
    }

    protected fun applyNavigationBarMargin(root: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                root.updateLayoutParams<FrameLayout.LayoutParams> {
                    val newInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                    this.updateMargins(
                        left = newInsets.left,
                        top = newInsets.top,
                        right = newInsets.right,
                        bottom = newInsets.bottom
                    )
                }
                insets
            }
        } else {
            root.fitsSystemWindows = true
        }
    }

    abstract fun getFileType(): String
    abstract fun viewFile()

    protected fun loadWholeFile(path: String, fileSize: Long? = null, callback: (ByteArray) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = encryptedVolume.loadWholeFile(path, size = fileSize)
            if (isActive) {
                withContext(Dispatchers.Main) {
                    if (result.second == 0) {
                        callback(result.first!!)
                    } else {
                        val dialog = CustomAlertDialogBuilder(this@FileViewerActivity, theme)
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

    protected suspend fun createPlaylist() {
        playlistMutex.withLock {
            if (fileViewerViewModel.currentPlaylistIndex != -1) {
                // playlist already initialized
                return
            }
            withContext(Dispatchers.IO) {
                if (sharedPrefs.getBoolean("map_folders", true)) {
                    encryptedVolume.recursiveMapFiles(originalParentPath)
                } else {
                    encryptedVolume.readDir(originalParentPath)
                }?.filterTo(fileViewerViewModel.playlist) { e ->
                    e.isRegularFile && (FileTypes.isExtensionType(getFileType(), e.name) || fileViewerViewModel.filePath == e.fullPath)
                }
                val sortOrder = intent.getStringExtra("sortOrder") ?: "name"
                val foldersFirst = sharedPrefs.getBoolean("folders_first", true)
                ExplorerElement.sortBy(sortOrder, foldersFirst, fileViewerViewModel.playlist)
                fileViewerViewModel.currentPlaylistIndex = fileViewerViewModel.playlist.indexOfFirst { it.fullPath == fileViewerViewModel.filePath }
            }
        }
    }

    private fun updateCurrentItem() {
        fileViewerViewModel.filePath = fileViewerViewModel.playlist[fileViewerViewModel.currentPlaylistIndex].fullPath
    }

    protected suspend fun playlistNext(forward: Boolean) {
        createPlaylist()
        fileViewerViewModel.currentPlaylistIndex = if (forward) {
            (fileViewerViewModel.currentPlaylistIndex + 1).mod(fileViewerViewModel.playlist.size)
        } else {
            (fileViewerViewModel.currentPlaylistIndex - 1).mod(fileViewerViewModel.playlist.size)
        }
        updateCurrentItem()
    }

    protected suspend fun deleteCurrentFile(): Boolean {
        createPlaylist() // ensure we know the current position in the playlist
        return if (encryptedVolume.deleteFile(fileViewerViewModel.filePath!!)) {
            fileViewerViewModel.playlist.removeAt(fileViewerViewModel.currentPlaylistIndex)
            if (fileViewerViewModel.playlist.isNotEmpty()) {
                if (fileViewerViewModel.currentPlaylistIndex == fileViewerViewModel.playlist.size) {
                    // deleted the last element of the playlist, go back to the first
                    fileViewerViewModel.currentPlaylistIndex = 0
                }
                updateCurrentItem()
            }
            true
        } else {
            false
        }
    }

    protected fun goBackToExplorer() {
        finish()
    }
}

package sushi.hardcore.droidfs.file_viewers

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

abstract class FileViewerActivity: BaseActivity() {

    class FileViewerViewModel : ViewModel() {
        val playlist = mutableListOf<ExplorerElement>()
        var currentPlaylistIndex = -1
        var filePath: String? = null
    }

    protected open val blackBackground: Boolean = false
    protected lateinit var encryptedVolume: EncryptedVolume
    protected val volumeId: Int by lazy { intent.getIntExtra("volumeId", -1) }
    private lateinit var originalParentPath: String
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private val playlistMutex = Mutex()
    protected val fileViewerViewModel: FileViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        if (blackBackground) {
            window.decorView.setBackgroundColor(Color.BLACK)
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false
        }
        if (fileViewerViewModel.filePath == null) {
            fileViewerViewModel.filePath = intent.getStringExtra("path")!!
        }
        originalParentPath = PathUtils.getParentPath(fileViewerViewModel.filePath!!)
        encryptedVolume = (application as VolumeManagerApp).volumeManager.getVolume(
            volumeId
        )!!
        finishOnClose(encryptedVolume)
        viewFile()
    }

    open fun showPartialSystemUi() {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    open fun hideSystemUi() {
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    abstract fun getFileType(): String
    abstract fun viewFile()

    protected fun loadWholeFile(path: String, fileSize: Long? = null, callback: (ByteArray) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val (fileBytes, errorCode) = encryptedVolume.loadWholeFile(path, size = fileSize)
            if (isActive) {
                withContext(Dispatchers.Main) {
                    if (errorCode == 0) {
                        callback(fileBytes!!)
                    } else {
                        MaterialAlertDialogBuilder(this@FileViewerActivity)
                            .setTitle(R.string.error)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok) { _, _ -> goBackToExplorer() }
                            .setMessage(EncryptedVolume.loadWholeFileErrorToString(this@FileViewerActivity, errorCode))
                            .show()
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

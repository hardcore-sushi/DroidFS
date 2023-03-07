package sushi.hardcore.droidfs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.Constants.DEFAULT_VOLUME_KEY
import sushi.hardcore.droidfs.adapters.VolumeAdapter
import sushi.hardcore.droidfs.add_volume.AddVolumeActivity
import sushi.hardcore.droidfs.databinding.ActivityMainBinding
import sushi.hardcore.droidfs.databinding.DialogDeleteVolumeBinding
import sushi.hardcore.droidfs.explorers.ExplorerRouter
import sushi.hardcore.droidfs.file_operations.FileOperationService
import sushi.hardcore.droidfs.util.IntentUtils
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog
import java.io.File

class MainActivity : BaseActivity(), VolumeAdapter.Listener {
    companion object {
        private const val OPEN_DEFAULT_VOLUME = "openDefault"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var volumeDatabase: VolumeDatabase
    private lateinit var volumeManager: VolumeManager
    private lateinit var volumeAdapter: VolumeAdapter
    private lateinit var volumeOpener: VolumeOpener
    private var addVolume = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if ((explorerRouter.pickMode || explorerRouter.dropMode) && result.resultCode != AddVolumeActivity.RESULT_USER_BACK) {
            setResult(result.resultCode, result.data) // forward result
            finish()
        }
    }
    private var changePasswordPosition: Int? = null
    private var changePassword = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        changePasswordPosition?.let { unselect(it) }
    }
    private val pickDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null)
            onDirectoryPicked(uri)
    }
    private lateinit var fileOperationService: FileOperationService
    private lateinit var explorerRouter: ExplorerRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (sharedPrefs.getBoolean("applicationFirstOpening", true)) {
            CustomAlertDialogBuilder(this, themeValue)
                .setTitle(R.string.warning)
                .setMessage(R.string.usf_home_warning_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.see_unsafe_features) { _, _ ->
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra("screen", "UnsafeFeaturesSettingsFragment")
                    startActivity(intent)
                }
                .setNegativeButton(R.string.ok, null)
                .setOnDismissListener {
                    with (sharedPrefs.edit()) {
                        putBoolean("applicationFirstOpening", false)
                        apply()
                    }
                }
                .show()
        }
        explorerRouter = ExplorerRouter(this, intent)
        volumeManager = (application as VolumeManagerApp).volumeManager
        volumeDatabase = VolumeDatabase(this)
        volumeAdapter = VolumeAdapter(
            this,
            volumeDatabase,
            (application as VolumeManagerApp).volumeManager,
            !explorerRouter.pickMode && !explorerRouter.dropMode,
            !explorerRouter.dropMode,
            this,
        )
        binding.recyclerViewVolumes.adapter = volumeAdapter
        binding.recyclerViewVolumes.layoutManager = LinearLayoutManager(this)
        if (volumeAdapter.volumes.isEmpty()) {
            binding.textNoVolumes.visibility = View.VISIBLE
        }
        if (explorerRouter.pickMode) {
            title = getString(R.string.select_volume)
        }
        binding.fab.setOnClickListener {
            addVolume.launch(Intent(this, AddVolumeActivity::class.java).also {
                if (explorerRouter.dropMode || explorerRouter.pickMode) {
                    IntentUtils.forwardIntent(intent, it)
                }
            })
        }
        volumeOpener = VolumeOpener(this)
        onBackPressedDispatcher.addCallback(this) {
            if (volumeAdapter.selectedItems.isNotEmpty()) {
                unselectAll()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        volumeOpener.defaultVolumeName?.let { name ->
            val state = savedInstanceState?.getBoolean(OPEN_DEFAULT_VOLUME)
            if (state == true || state == null) {
                val volumeData = volumeAdapter.volumes.first { it.name == name }
                if (!volumeManager.isOpen(volumeData)) {
                    try {
                        openVolume(volumeData)
                    } catch (e: NoSuchElementException) {
                        unsetDefaultVolume()
                    }
                }
            }
        }
        Intent(this, FileOperationService::class.java).also {
            bindService(it, object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    fileOperationService = (service as FileOperationService.LocalBinder).getService()
                }
                override fun onServiceDisconnected(arg0: ComponentName) {}
            }, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStart() {
        super.onStart()
        // refresh theme if changed in SettingsActivity
        val newThemeValue = sharedPrefs.getString(Constants.THEME_VALUE_KEY, Constants.DEFAULT_THEME_VALUE)!!
        onThemeChanged(newThemeValue)
        volumeOpener.themeValue = newThemeValue
        volumeAdapter.refresh()
        if (volumeAdapter.volumes.isNotEmpty()) {
            binding.textNoVolumes.visibility = View.GONE
        }
        // refresh this in case another instance of MainActivity changes its value
        volumeOpener.defaultVolumeName = sharedPrefs.getString(DEFAULT_VOLUME_KEY, null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DEFAULT_VOLUME, false)
    }

    override fun onSelectionChanged(size: Int) {
        title = if (size == 0) {
            getString(R.string.app_name)
        } else {
            getString(R.string.elements_selected, size, volumeAdapter.volumes.size)
        }
    }

    override fun onVolumeItemClick(volume: VolumeData, position: Int) {
        if (volumeAdapter.selectedItems.isEmpty())
            openVolume(volume)
        else
            invalidateOptionsMenu()
    }

    override fun onVolumeItemLongClick() {
        invalidateOptionsMenu()
    }

    private fun unselectAll(notifyChange: Boolean = true) {
        volumeAdapter.unSelectAll(notifyChange)
        invalidateOptionsMenu()
    }

    private fun unselect(position: Int) {
        volumeAdapter.selectedItems.remove(position)
        volumeAdapter.onVolumeChanged(position)
        onSelectionChanged(0) // unselect() is always called when only one element is selected
        invalidateOptionsMenu()
    }

    private fun removeVolume(volume: VolumeData) {
        volumeManager.getVolumeId(volume)?.let { volumeManager.closeVolume(it) }
        volumeDatabase.removeVolume(volume.name)
    }

    private fun removeVolumes(volumes: List<VolumeData>, i: Int = 0, doDeleteVolumeContent: Boolean? = null) {
        if (i < volumes.size) {
            if (volumes[i].isHidden) {
                if (doDeleteVolumeContent == null) {
                    val dialogBinding = DialogDeleteVolumeBinding.inflate(layoutInflater)
                    dialogBinding.textContent.text = getString(R.string.delete_hidden_volume_question, volumes[i].name)
                    // show checkbox only if there is at least one other hidden volume
                    for (j in (i+1 until volumes.size)) {
                        if (volumes[j].isHidden) {
                            dialogBinding.checkboxApplyToAll.visibility = View.VISIBLE
                            break
                        }
                    }
                    CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.warning)
                        .setView(dialogBinding.root)
                        .setPositiveButton(R.string.forget_only) { _, _ ->
                            removeVolume(volumes[i])
                            removeVolumes(volumes, i + 1, if (dialogBinding.checkboxApplyToAll.isChecked) false else null)
                        }
                        .setNegativeButton(R.string.delete_volume) { _, _ ->
                            PathUtils.recursiveRemoveDirectory(File(volumes[i].getFullPath(filesDir.path)))
                            removeVolume(volumes[i])
                            removeVolumes(volumes, i + 1, if (dialogBinding.checkboxApplyToAll.isChecked) true else null)
                        }
                        .setOnCancelListener {
                            volumeAdapter.refresh()
                            invalidateOptionsMenu()
                        }
                        .show()
                } else {
                    if (doDeleteVolumeContent) {
                        PathUtils.recursiveRemoveDirectory(File(volumes[i].getFullPath(filesDir.path)))
                    }
                    removeVolume(volumes[i])
                    removeVolumes(volumes, i + 1, doDeleteVolumeContent)
                }
            } else {
                removeVolume(volumes[i])
                removeVolumes(volumes, i + 1, doDeleteVolumeContent)
            }
        } else {
            volumeAdapter.refresh()
            invalidateOptionsMenu()
            if (volumeAdapter.volumes.isEmpty()) {
                binding.textNoVolumes.visibility = View.VISIBLE
            }
        }
    }

    private fun unsetDefaultVolume() {
        with (sharedPrefs.edit()) {
            remove(DEFAULT_VOLUME_KEY)
            apply()
        }
        volumeOpener.defaultVolumeName = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (explorerRouter.pickMode || explorerRouter.dropMode) {
                    finish()
                } else {
                    unselectAll()
                }
                true
            }
            R.id.select_all -> {
                volumeAdapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.lock -> {
                volumeAdapter.selectedItems.forEach {
                    volumeManager.getVolumeId(volumeAdapter.volumes[it])?.let { id ->
                        volumeManager.closeVolume(id)
                    }
                }
                unselectAll()
                true
            }
            R.id.remove -> {
                val selectedVolumes = volumeAdapter.selectedItems.map { i -> volumeAdapter.volumes[i] }
                removeVolumes(selectedVolumes)
                true
            }
            R.id.delete_password_hash -> {
                for (i in volumeAdapter.selectedItems) {
                    if (volumeDatabase.removeHash(volumeAdapter.volumes[i]))
                        volumeAdapter.onVolumeChanged(i)
                }
                unselectAll(false)
                true
            }
            R.id.change_password -> {
                changePasswordPosition = volumeAdapter.selectedItems.elementAt(0)
                changePassword.launch(Intent(this, ChangePasswordActivity::class.java).apply {
                    putExtra("volume", volumeAdapter.volumes[changePasswordPosition!!])
                })
                true
            }
            R.id.remove_default_open -> {
                unsetDefaultVolume()
                unselect(volumeAdapter.selectedItems.first())
                true
            }
            R.id.copy -> {
                val position = volumeAdapter.selectedItems.elementAt(0)
                val volume = volumeAdapter.volumes[position]
                when {
                    volume.isHidden -> {
                        PathUtils.safePickDirectory(pickDirectory, this, themeValue)
                    }
                    File(filesDir, volume.shortName).exists() -> {
                        CustomAlertDialogBuilder(this, themeValue)
                            .setTitle(R.string.error)
                            .setMessage(R.string.hidden_volume_already_exists)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                    else -> {
                        unselect(position)
                        copyVolume(
                            DocumentFile.fromFile(File(volume.name)),
                            DocumentFile.fromFile(filesDir),
                        ) {
                            VolumeData(volume.shortName, true, volume.type, volume.encryptedHash, volume.iv)
                        }
                    }
                }
                true
            }
            R.id.rename -> {
                val position = volumeAdapter.selectedItems.elementAt(0)
                renameVolume(volumeAdapter.volumes[position], position)
                true
            }
            R.id.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        menu.findItem(R.id.settings).isVisible = !explorerRouter.pickMode && !explorerRouter.dropMode
        val isSelecting = volumeAdapter.selectedItems.isNotEmpty()
        menu.findItem(R.id.select_all).isVisible = isSelecting
        menu.findItem(R.id.lock).isVisible = isSelecting && volumeAdapter.selectedItems.any {
            i -> volumeManager.isOpen(volumeAdapter.volumes[i])
        }
        menu.findItem(R.id.remove).isVisible = isSelecting
        menu.findItem(R.id.delete_password_hash).isVisible =
            isSelecting &&
            !volumeAdapter.selectedItems.any { i -> volumeAdapter.volumes[i].encryptedHash == null }
        val onlyOneSelected = volumeAdapter.selectedItems.size == 1
        val onlyOneAndWriteable =
            onlyOneSelected &&
            volumeAdapter.volumes[volumeAdapter.selectedItems.first()].canWrite(filesDir.path)
        menu.findItem(R.id.change_password).isVisible = onlyOneAndWriteable
        menu.findItem(R.id.remove_default_open).isVisible =
            onlyOneSelected &&
            volumeAdapter.volumes[volumeAdapter.selectedItems.first()].name == volumeOpener.defaultVolumeName
        with(menu.findItem(R.id.copy)) {
            isVisible = onlyOneSelected
            if (isVisible) {
                setTitle(if (volumeAdapter.volumes[volumeAdapter.selectedItems.elementAt(0)].isHidden)
                    R.string.copy_hidden_volume
                else
                    R.string.copy_external_volume
                )
            }
        }
        menu.findItem(R.id.rename).isVisible = onlyOneAndWriteable
        supportActionBar?.setDisplayHomeAsUpEnabled(isSelecting || explorerRouter.pickMode || explorerRouter.dropMode)
        return true
    }

    private fun onDirectoryPicked(uri: Uri) {
        val position = volumeAdapter.selectedItems.elementAt(0)
        val volume = volumeAdapter.volumes[position]
        unselect(position)
        val dstDocumentFile = DocumentFile.fromTreeUri(this, uri)
        if (dstDocumentFile == null) {
            CustomAlertDialogBuilder(this, themeValue)
                .setTitle(R.string.error)
                .setMessage(R.string.path_error)
                .setPositiveButton(R.string.ok, null)
                .show()
        } else {
            copyVolume(
                DocumentFile.fromFile(File(volume.getFullPath(filesDir.path))),
                dstDocumentFile,
            ) { dstRootDirectory ->
                dstRootDirectory.name?.let { name ->
                    val path = PathUtils.getFullPathFromTreeUri(dstRootDirectory.uri, this)
                    if (path == null) null
                    else VolumeData(
                        PathUtils.pathJoin(path, name),
                        false,
                        volume.type,
                        volume.encryptedHash,
                        volume.iv
                    )
                }
            }
        }
    }

    private fun copyVolume(srcDocumentFile: DocumentFile, dstDocumentFile: DocumentFile, getResultVolume: (DocumentFile) -> VolumeData?) {
        lifecycleScope.launch {
            val result = fileOperationService.copyVolume(srcDocumentFile, dstDocumentFile)
            when {
                result.taskResult.cancelled -> {
                    result.dstRootDirectory?.delete()
                }
                result.taskResult.failedItem == null -> {
                    result.dstRootDirectory?.let {
                        getResultVolume(it)?.let { volume ->
                            volumeDatabase.saveVolume(volume)
                            volumeAdapter.apply {
                                volumes = volumeDatabase.getVolumes()
                                notifyItemInserted(volumes.size)
                            }
                            binding.textNoVolumes.visibility = View.GONE
                            Toast.makeText(this@MainActivity, R.string.copy_success, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                else -> {
                    CustomAlertDialogBuilder(this@MainActivity, themeValue)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.copy_failed, result.taskResult.failedItem.name))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun renameVolume(volume: VolumeData, position: Int) {
        with (EditTextDialog(this, R.string.new_volume_name) { newName ->
            val srcPath = File(volume.getFullPath(filesDir.path))
            val dstPath = File(srcPath.parent, newName).canonicalFile
            val newDBName: String
            val success = if (volume.isHidden) {
                if (newName.contains(PathUtils.SEPARATOR)) {
                    Toast.makeText(this, R.string.error_slash_in_name, Toast.LENGTH_SHORT).show()
                    renameVolume(volume, position)
                    return@EditTextDialog
                }
                newDBName = newName
                srcPath.renameTo(dstPath)
            } else {
                newDBName = dstPath.path
                DocumentFile.fromFile(srcPath).renameTo(newName)
            }
            if (success) {
                volumeDatabase.renameVolume(volume.name, newDBName)
                unselect(position)
                if (volume.name == volumeOpener.defaultVolumeName) {
                    with (sharedPrefs.edit()) {
                        putString(DEFAULT_VOLUME_KEY, newDBName)
                        apply()
                    }
                    volumeOpener.defaultVolumeName = newDBName
                }
            } else {
                Toast.makeText(this, R.string.volume_rename_failed, Toast.LENGTH_SHORT).show()
            }
        }) {
            setSelectedText(volume.shortName)
            show()
        }
    }

    private fun openVolume(volume: VolumeData) {
        volumeOpener.openVolume(volume, true, object : VolumeOpener.VolumeOpenerCallbacks {
            override fun onHashStorageReset() {
                volumeAdapter.refresh()
            }

            override fun onVolumeOpened(id: Int) {
                startActivity(explorerRouter.getExplorerIntent(id, volume.shortName))
                if (explorerRouter.dropMode || explorerRouter.pickMode) {
                    finish()
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()
        volumeOpener.wipeSensitive()
    }
}

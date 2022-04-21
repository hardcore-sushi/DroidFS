package sushi.hardcore.droidfs

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.adapters.VolumeAdapter
import sushi.hardcore.droidfs.add_volume.AddVolumeActivity
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.databinding.ActivityMainBinding
import sushi.hardcore.droidfs.databinding.DialogDeleteVolumeBinding
import sushi.hardcore.droidfs.databinding.DialogOpenVolumeBinding
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerActivityDrop
import sushi.hardcore.droidfs.explorers.ExplorerActivityPick
import sushi.hardcore.droidfs.file_operations.FileOperationService
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog
import java.io.File
import java.util.*

class MainActivity : BaseActivity(), VolumeAdapter.Listener {

    companion object {
        const val DEFAULT_VOLUME_KEY = "default_volume"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var volumeDatabase: VolumeDatabase
    private lateinit var volumeAdapter: VolumeAdapter
    private var fingerprintProtector: FingerprintProtector? = null
    private var usfFingerprint: Boolean = false
    private var usfKeepOpen: Boolean = false
    private var defaultVolumeName: String? = null
    private var addVolume = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            AddVolumeActivity.RESULT_VOLUME_ADDED -> onVolumeAdded()
            AddVolumeActivity.RESULT_HASH_STORAGE_RESET -> {
                volumeAdapter.refresh()
                binding.textNoVolumes.visibility = View.GONE
            }
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
    private var pickMode = false
    private var dropMode = false
    private var shouldCloseVolume = true // used when launched to pick file from another volume

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
        pickMode = intent.action == "pick"
        dropMode = (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) && intent.extras != null
        volumeDatabase = VolumeDatabase(this)
        volumeAdapter = VolumeAdapter(
            this,
            volumeDatabase,
            !pickMode && !dropMode,
            !dropMode,
            this,
        )
        binding.recyclerViewVolumes.adapter = volumeAdapter
        binding.recyclerViewVolumes.layoutManager = LinearLayoutManager(this)
        if (volumeAdapter.volumes.isEmpty()) {
            binding.textNoVolumes.visibility = View.VISIBLE
        }
        if (pickMode) {
            title = getString(R.string.select_volume)
            binding.fab.visibility = View.GONE
        } else {
            binding.fab.setOnClickListener {
                addVolume.launch(Intent(this, AddVolumeActivity::class.java))
            }
        }
        usfKeepOpen = sharedPrefs.getBoolean("usf_keep_open", false)
        usfFingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fingerprintProtector = FingerprintProtector.new(this, themeValue, volumeDatabase)
        }
        defaultVolumeName = sharedPrefs.getString(DEFAULT_VOLUME_KEY, null)
        defaultVolumeName?.let { name ->
            try {
                val (position, volume) = volumeAdapter.volumes.withIndex().first { it.value.name == name }
                openVolume(volume, position)
            } catch (e: NoSuchElementException) {
                unsetDefaultVolume()
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
        // refresh this in case another instance of MainActivity changes its value
        defaultVolumeName = sharedPrefs.getString(DEFAULT_VOLUME_KEY, null)
    }

    override fun onSelectionChanged(size: Int) {
        title = if (size == 0) {
            getString(R.string.app_name)
        } else {
            getString(R.string.elements_selected, size, volumeAdapter.volumes.size)
        }
    }

    override fun onVolumeItemClick(volume: Volume, position: Int) {
        if (volumeAdapter.selectedItems.isEmpty())
            openVolume(volume, position)
        else
            invalidateOptionsMenu()
    }

    override fun onVolumeItemLongClick() {
        invalidateOptionsMenu()
    }

    private fun onVolumeAdded() {
        volumeAdapter.apply {
            volumes = volumeDatabase.getVolumes()
            notifyItemInserted(volumes.size)
        }
        binding.textNoVolumes.visibility = View.GONE
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

    private fun removeVolumes(volumes: List<Volume>, i: Int = 0, doDeleteVolumeContent: Boolean? = null) {
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
                            volumeDatabase.removeVolume(volumes[i].name)
                            removeVolumes(volumes, i + 1, if (dialogBinding.checkboxApplyToAll.isChecked) false else null)
                        }
                        .setNegativeButton(R.string.delete_volume) { _, _ ->
                            PathUtils.recursiveRemoveDirectory(File(volumes[i].getFullPath(filesDir.path)))
                            volumeDatabase.removeVolume(volumes[i].name)
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
                    volumeDatabase.removeVolume(volumes[i].name)
                    removeVolumes(volumes, i + 1, doDeleteVolumeContent)
                }
            } else {
                volumeDatabase.removeVolume(volumes[i].name)
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
        defaultVolumeName = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (pickMode || dropMode) {
                    if (pickMode)
                        shouldCloseVolume = false
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
            R.id.remove -> {
                val selectedVolumes = volumeAdapter.selectedItems.map { i -> volumeAdapter.volumes[i] }
                removeVolumes(selectedVolumes)
                true
            }
            R.id.forget_password -> {
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
                            Volume(volume.shortName, true, volume.encryptedHash, volume.iv)
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
        menu.findItem(R.id.settings).isVisible = !pickMode && !dropMode
        val isSelecting = volumeAdapter.selectedItems.isNotEmpty()
        menu.findItem(R.id.select_all).isVisible = isSelecting
        menu.findItem(R.id.remove).isVisible = isSelecting
        menu.findItem(R.id.forget_password).isVisible =
            isSelecting &&
            !volumeAdapter.selectedItems.any { i -> volumeAdapter.volumes[i].encryptedHash == null }
        val onlyOneSelected = volumeAdapter.selectedItems.size == 1
        val onlyOneAndWriteable =
            onlyOneSelected &&
            volumeAdapter.volumes[volumeAdapter.selectedItems.first()].canWrite(filesDir.path)
        menu.findItem(R.id.change_password).isVisible = onlyOneAndWriteable
        menu.findItem(R.id.remove_default_open).isVisible =
            onlyOneSelected &&
            volumeAdapter.volumes[volumeAdapter.selectedItems.first()].name == defaultVolumeName
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
        supportActionBar?.setDisplayHomeAsUpEnabled(isSelecting || pickMode || dropMode)
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
                    else Volume(
                        PathUtils.pathJoin(path, name),
                        false,
                        volume.encryptedHash,
                        volume.iv
                    )
                }
            }
        }
    }

    private fun copyVolume(srcDocumentFile: DocumentFile, dstDocumentFile: DocumentFile, getResultVolume: (DocumentFile) -> Volume?) {
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
                            onVolumeAdded()
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

    private fun renameVolume(volume: Volume, position: Int) {
        with (EditTextDialog(this, R.string.new_volume_name) { newName ->
            val srcPath = File(volume.getFullPath(filesDir.path))
            val dstPath = File(srcPath.parent, newName).canonicalFile
            val newDBName: String
            val success = if (volume.isHidden) {
                if (newName.contains("/")) {
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
                if (volume.name == defaultVolumeName) {
                    with (sharedPrefs.edit()) {
                        putString(DEFAULT_VOLUME_KEY, newDBName)
                        apply()
                    }
                    defaultVolumeName = newDBName
                }
            } else {
                Toast.makeText(this, R.string.volume_rename_failed, Toast.LENGTH_SHORT).show()
            }
        }) {
            setSelectedText(volume.shortName)
            show()
        }
    }

    @SuppressLint("NewApi") // fingerprintProtector is non-null only when SDK_INT >= 23
    private fun openVolume(volume: Volume, position: Int) {
        var askForPassword = true
        fingerprintProtector?.let { fingerprintProtector ->
            volume.encryptedHash?.let { encryptedHash ->
                volume.iv?.let { iv ->
                    askForPassword = false
                    fingerprintProtector.listener = object : FingerprintProtector.Listener {
                        override fun onHashStorageReset() {
                            volumeAdapter.refresh()
                        }
                        override fun onPasswordHashDecrypted(hash: ByteArray) {
                            object : LoadingTask<Int>(this@MainActivity, themeValue, R.string.loading_msg_open) {
                                override suspend fun doTask(): Int {
                                    val sessionId = GocryptfsVolume.init(volume.getFullPath(filesDir.path), null, hash, null)
                                    Arrays.fill(hash, 0)
                                    return sessionId
                                }
                            }.startTask(lifecycleScope) { sessionId ->
                                if (sessionId != -1) {
                                    startExplorer(sessionId, volume.shortName)
                                } else {
                                    CustomAlertDialogBuilder(this@MainActivity, themeValue)
                                        .setTitle(R.string.open_volume_failed)
                                        .setMessage(R.string.open_failed_hash_msg)
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                                }
                            }
                        }
                        override fun onPasswordHashSaved() {}
                        override fun onFailed(pending: Boolean) {
                            if (!pending) {
                                askForPassword(volume, position)
                            }
                        }
                    }
                    fingerprintProtector.loadPasswordHash(volume.shortName, encryptedHash, iv)
                }
            }
        }
        if (askForPassword)
            askForPassword(volume, position)
    }

    private fun onPasswordSubmitted(volume: Volume, position: Int, dialogBinding: DialogOpenVolumeBinding) {
        if (dialogBinding.checkboxDefaultOpen.isChecked xor (defaultVolumeName == volume.name)) {
            with (sharedPrefs.edit()) {
                defaultVolumeName = if (dialogBinding.checkboxDefaultOpen.isChecked) {
                    putString(DEFAULT_VOLUME_KEY, volume.name)
                    volume.name
                } else {
                    remove(DEFAULT_VOLUME_KEY)
                    null
                }
                apply()
            }
        }
        val password = CharArray(dialogBinding.editPassword.text.length)
        dialogBinding.editPassword.text.getChars(0, password.size, password, 0)
        // openVolumeWithPassword is responsible for wiping the password
        openVolumeWithPassword(
            volume,
            position,
            password,
            dialogBinding.checkboxSavePassword.isChecked,
        )
    }

    private fun askForPassword(volume: Volume, position: Int, savePasswordHash: Boolean = false) {
        val dialogBinding = DialogOpenVolumeBinding.inflate(layoutInflater)
        if (!usfFingerprint || fingerprintProtector == null || volume.encryptedHash != null) {
            dialogBinding.checkboxSavePassword.visibility = View.GONE
        } else {
            dialogBinding.checkboxSavePassword.isChecked = savePasswordHash
        }
        dialogBinding.checkboxDefaultOpen.isChecked = defaultVolumeName == volume.name
        val dialog = CustomAlertDialogBuilder(this, themeValue)
            .setTitle(getString(R.string.open_dialog_title, volume.shortName))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open) { _, _ ->
                onPasswordSubmitted(volume, position, dialogBinding)
            }
            .create()
        dialogBinding.editPassword.apply {
            setOnEditorActionListener { _, _, _ ->
                dialog.dismiss()
                onPasswordSubmitted(volume, position, dialogBinding)
                true
            }
            if (sharedPrefs.getBoolean(ConstValues.PIN_PASSWORDS_KEY, false)) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    private fun openVolumeWithPassword(volume: Volume, position: Int, password: CharArray, savePasswordHash: Boolean) {
        val usfFingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        var returnedHash: ByteArray? = null
        if (savePasswordHash && usfFingerprint) {
            returnedHash = ByteArray(GocryptfsVolume.KeyLen)
        }
        object : LoadingTask<Int>(this, themeValue, R.string.loading_msg_open) {
            override suspend fun doTask(): Int {
                val sessionId = GocryptfsVolume.init(volume.getFullPath(filesDir.path), password, null, returnedHash)
                Arrays.fill(password, 0.toChar())
                return sessionId
            }
        }.startTask(lifecycleScope) { sessionId ->
            if (sessionId != -1) {
                val fingerprintProtector = fingerprintProtector
                @SuppressLint("NewApi") // fingerprintProtector is non-null only when SDK_INT >= 23
                if (savePasswordHash && returnedHash != null && fingerprintProtector != null) {
                    fingerprintProtector.listener = object : FingerprintProtector.Listener {
                        override fun onHashStorageReset() {
                            volumeAdapter.refresh()
                        }
                        override fun onPasswordHashDecrypted(hash: ByteArray) {}
                        override fun onPasswordHashSaved() {
                            Arrays.fill(returnedHash, 0)
                            volumeAdapter.onVolumeChanged(position)
                            startExplorer(sessionId, volume.shortName)
                        }
                        private var isClosed = false
                        override fun onFailed(pending: Boolean) {
                            if (!isClosed) {
                                GocryptfsVolume(this@MainActivity, sessionId).close()
                                isClosed = true
                            }
                            Arrays.fill(returnedHash, 0)
                        }
                    }
                    fingerprintProtector.savePasswordHash(volume, returnedHash)
                } else {
                    startExplorer(sessionId, volume.shortName)
                }
            } else {
                CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.open_volume_failed)
                    .setMessage(R.string.open_volume_failed_msg)
                    .setPositiveButton(R.string.ok, null)
                    .setOnDismissListener {
                        askForPassword(volume, position, savePasswordHash)
                    }
                    .show()
            }
        }
    }

    private fun startExplorer(sessionId: Int, volumeShortName: String) {
        var explorerIntent: Intent? = null
        if (dropMode) { //import via android share menu
            explorerIntent = Intent(this, ExplorerActivityDrop::class.java)
            explorerIntent.action = intent.action //forward action
            explorerIntent.putExtras(intent.extras!!) //forward extras
        } else if (pickMode) {
            explorerIntent = Intent(this, ExplorerActivityPick::class.java)
            explorerIntent.putExtra("originalSessionID", intent.getIntExtra("sessionID", -1))
            explorerIntent.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
        }
        if (explorerIntent == null) {
            explorerIntent = Intent(this, ExplorerActivity::class.java) //default opening
        }
        explorerIntent.putExtra("sessionID", sessionId)
        explorerIntent.putExtra("volume_name", volumeShortName)
        startActivity(explorerIntent)
        if (pickMode)
            shouldCloseVolume = false
        if (dropMode || pickMode)
            finish()
    }

    override fun onBackPressed() {
        if (volumeAdapter.selectedItems.isNotEmpty()) {
            unselectAll()
        } else {
            if (pickMode)
                shouldCloseVolume = false
            super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        if (pickMode && !usfKeepOpen) {
            finish()
            if (shouldCloseVolume) {
                val sessionID = intent.getIntExtra("sessionID", -1)
                if (sessionID != -1) {
                    GocryptfsVolume(this, sessionID).close()
                    RestrictedFileProvider.wipeAll(this)
                }
            }
        }
    }
}

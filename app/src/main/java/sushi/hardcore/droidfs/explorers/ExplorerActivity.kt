package sushi.hardcore.droidfs.explorers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.CameraActivity
import sushi.hardcore.droidfs.LoadingTask
import sushi.hardcore.droidfs.MainActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.adapters.IconTextDialogAdapter
import sushi.hardcore.droidfs.file_operations.OperationFile
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog

class ExplorerActivity : BaseExplorerActivity() {
    companion object {
        private enum class ItemsActions {NONE, COPY, MOVE}
    }

    private var usf_decrypt = false
    private var usf_share = false
    private var currentItemAction = ItemsActions.NONE
    private val itemsToProcess = ArrayList<OperationFile>()
    private val pickFromOtherVolumes = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { resultIntent ->
                val srcVolumeId = resultIntent.getIntExtra("volumeId", -1)
                val srcEncryptedVolume = app.volumeManager.getVolume(srcVolumeId)!!
                val path = resultIntent.getStringExtra("path")
                if (path == null){ //multiples elements
                    val paths = resultIntent.getStringArrayListExtra("paths")
                    val types = resultIntent.getIntegerArrayListExtra("types")
                    if (types != null && paths != null){
                        object : LoadingTask<List<OperationFile>>(this, theme, R.string.discovering_files) {
                            override suspend fun doTask(): List<OperationFile> {
                                val operationFiles = ArrayList<OperationFile>()
                                for (i in paths.indices) {
                                    operationFiles.add(OperationFile(paths[i], types[i]))
                                    if (types[i] == Stat.S_IFDIR) {
                                        srcEncryptedVolume.recursiveMapFiles(paths[i])?.forEach {
                                            operationFiles.add(OperationFile.fromExplorerElement(it))
                                        }
                                    }
                                }
                                return operationFiles
                            }
                        }.startTask(lifecycleScope) { operationFiles ->
                            importFilesFromVolume(srcVolumeId, operationFiles)
                        }
                    }
                } else {
                    importFilesFromVolume(srcVolumeId, arrayListOf(OperationFile(path, Stat.S_IFREG)))
                }
            }
        }
    }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null) {
            for (uri in uris) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            importFilesFromUris(uris) {
                onImportComplete(uris)
            }
        }
    }
    private val pickExportDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val items = explorerAdapter.selectedItems.map { i -> explorerElements[i] }
            activityScope.launch {
                val result = fileOperationService.exportFiles(volumeId, items, uri)
                onTaskResult(result, R.string.export_failed, R.string.success_export)
            }
        }
        unselectAll()
    }
    private val pickImportDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { rootUri ->
        rootUri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val tree = DocumentFile.fromTreeUri(this, it)!! //non-null after Lollipop
            val operation = OperationFile(PathUtils.pathJoin(currentDirectoryPath, tree.name!!), Stat.S_IFDIR)
            checkPathOverwrite(arrayListOf(operation), currentDirectoryPath) { checkedOperation ->
                checkedOperation?.let {
                    activityScope.launch {
                        val result = fileOperationService.importDirectory(volumeId, checkedOperation[0].dstPath!!, tree)
                        onTaskResult(result.taskResult, R.string.import_failed) {
                            onImportComplete(result.uris, tree)
                        }
                        setCurrentPath(currentDirectoryPath)
                    }
                }
            }
        }
    }

    private fun importFilesFromVolume(srcVolumeId: Int, operationFiles: List<OperationFile>) {
        checkPathOverwrite(operationFiles, currentDirectoryPath) { items ->
            if (items != null) {
                // stop loading thumbnails while writing files
                explorerAdapter.loadThumbnails = false
                activityScope.launch {
                    onTaskResult(
                        fileOperationService.copyElements(
                            volumeId,
                            items,
                            srcVolumeId
                        ), R.string.import_failed, R.string.success_import
                    )
                    explorerAdapter.loadThumbnails = true
                    setCurrentPath(currentDirectoryPath)
                }
            }
        }

    }

    private fun onImportComplete(urisToWipe: List<Uri>, rootFile: DocumentFile? = null) {
        CustomAlertDialogBuilder(this, theme)
            .setTitle(R.string.success_import)
            .setMessage("""
                            ${getString(R.string.success_import_msg)}
                            ${getString(R.string.ask_for_wipe)}
                            """.trimIndent())
            .setPositiveButton(R.string.yes) { _, _ ->
                activityScope.launch {
                    onTaskResult(
                        fileOperationService.wipeUris(urisToWipe, rootFile),
                        R.string.wipe_failed,
                        R.string.wipe_successful,
                    )
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun init() {
        super.init()
        onBackPressedDispatcher.addCallback(this) {
            if (currentItemAction != ItemsActions.NONE) {
                cancelItemAction()
                invalidateOptionsMenu()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            if (currentItemAction != ItemsActions.NONE){
                openDialogCreateFolder()
            } else {
                val adapter = IconTextDialogAdapter(this)
                adapter.items = listOf(
                    listOf("importFromOtherVolumes", R.string.import_from_other_volume, R.drawable.icon_transfer),
                    listOf("importFiles", R.string.import_files, R.drawable.icon_encrypt),
                    listOf("importFolder", R.string.import_folder, R.drawable.icon_import_folder),
                    listOf("createFile", R.string.new_file, R.drawable.icon_file_unknown),
                    listOf("createFolder", R.string.mkdir, R.drawable.icon_create_new_folder),
                    listOf("camera", R.string.camera, R.drawable.icon_photo)
                )
                CustomAlertDialogBuilder(this, theme)
                    .setSingleChoiceItems(adapter, -1){ thisDialog, which ->
                        when (adapter.getItem(which)){
                            "importFromOtherVolumes" -> {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.action = "pick"
                                intent.putExtra("volume", encryptedVolume)
                                pickFromOtherVolumes.launch(intent)
                            }
                            "importFiles" -> {
                                app.isStartingExternalApp = true
                                pickFiles.launch(arrayOf("*/*"))
                            }
                            "importFolder" -> {
                                app.isStartingExternalApp = true
                                pickImportDirectory.launch(null)
                            }
                            "createFile" -> {
                                EditTextDialog(this, R.string.enter_file_name) {
                                    createNewFile(it)
                                }.show()
                            }
                            "createFolder" -> {
                                openDialogCreateFolder()
                            }
                            "camera" -> {
                                val intent = Intent(this, CameraActivity::class.java)
                                intent.putExtra("path", currentDirectoryPath)
                                intent.putExtra("volume", encryptedVolume)
                                startActivity(intent)
                            }
                        }
                        thisDialog.dismiss()
                    }
                    .setTitle(getString(R.string.add))
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        usf_decrypt = sharedPrefs.getBoolean("usf_decrypt", false)
        usf_share = sharedPrefs.getBoolean("usf_share", false)
    }

    override fun onExplorerElementLongClick(position: Int) {
        super.onExplorerElementLongClick(position)
        cancelItemAction()
    }

    private fun createNewFile(fileName: String){
        if (fileName.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            val filePath = PathUtils.pathJoin(currentDirectoryPath, fileName)
            val handleID = encryptedVolume.openFileWriteMode(filePath)
            if (handleID == -1L) {
                CustomAlertDialogBuilder(this, theme)
                        .setTitle(R.string.error)
                        .setMessage(R.string.file_creation_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show()
            } else {
                encryptedVolume.closeFile(handleID)
                setCurrentPath(currentDirectoryPath)
                invalidateOptionsMenu()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer, menu)
        val result = super.onCreateOptionsMenu(menu)
        if (currentItemAction != ItemsActions.NONE) {
            menu.findItem(R.id.validate).isVisible = true
            menu.findItem(R.id.lock).isVisible = false
            menu.findItem(R.id.close).isVisible = false
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        } else {
            if (usf_share){
                menu.findItem(R.id.share).isVisible = false
            }
            val anyItemSelected = explorerAdapter.selectedItems.isNotEmpty()
            menu.findItem(R.id.select_all).isVisible = anyItemSelected
            menu.findItem(R.id.delete).isVisible = anyItemSelected
            menu.findItem(R.id.copy).isVisible = anyItemSelected
            menu.findItem(R.id.cut).isVisible = anyItemSelected
            menu.findItem(R.id.decrypt).isVisible = anyItemSelected && usf_decrypt
            if (anyItemSelected && usf_share){
                var containsDir = false
                for (i in explorerAdapter.selectedItems) {
                    if (explorerElements[i].isDirectory) {
                        containsDir = true
                        break
                    }
                }
                if (!containsDir) {
                    menu.findItem(R.id.share).isVisible = true
                }
            }
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                cancelItemAction()
                super.onOptionsItemSelected(item)
            }
            R.id.select_all -> {
                explorerAdapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.cut -> {
                for (i in explorerAdapter.selectedItems){
                    itemsToProcess.add(OperationFile.fromExplorerElement(explorerElements[i]))
                }
                currentItemAction = ItemsActions.MOVE
                unselectAll()
                true
            }
            R.id.copy -> {
                for (i in explorerAdapter.selectedItems){
                    itemsToProcess.add(OperationFile.fromExplorerElement(explorerElements[i]))
                }
                currentItemAction = ItemsActions.COPY
                unselectAll()
                true
            }
            R.id.validate -> {
                if (currentItemAction == ItemsActions.COPY){
                    object : LoadingTask<List<OperationFile>>(this, theme, R.string.discovering_files) {
                        override suspend fun doTask(): List<OperationFile> {
                            val items = itemsToProcess.toMutableList()
                            itemsToProcess.filter { it.isDirectory }.forEach { dir ->
                                encryptedVolume.recursiveMapFiles(dir.srcPath)?.forEach {
                                    items.add(OperationFile.fromExplorerElement(it))
                                }
                            }
                            return items
                        }
                    }.startTask(lifecycleScope) { items ->
                        checkPathOverwrite(items, currentDirectoryPath) {
                            it?.let { checkedItems ->
                                activityScope.launch {
                                    onTaskResult(
                                        fileOperationService.copyElements(volumeId, checkedItems),
                                        R.string.copy_failed,
                                        R.string.copy_success,
                                    )
                                    setCurrentPath(currentDirectoryPath)
                                }
                            }
                            cancelItemAction()
                            invalidateOptionsMenu()
                        }
                    }
                } else if (currentItemAction == ItemsActions.MOVE){
                    itemsToProcess.forEach {
                        it.dstPath = PathUtils.pathJoin(currentDirectoryPath, it.name)
                        it.overwriteConfirmed = false // reset the field in case of a previous cancelled move
                    }
                    val toMove = ArrayList<OperationFile>(itemsToProcess.size)
                    val toClean = ArrayList<String>()
                    prepareFilesForMove(
                        itemsToProcess,
                        toMove,
                        toClean,
                    ) {
                        activityScope.launch {
                            onTaskResult(
                                fileOperationService.moveElements(volumeId, toMove, toClean),
                                R.string.move_success,
                                R.string.move_failed,
                            )
                            setCurrentPath(currentDirectoryPath)
                        }
                        cancelItemAction()
                        invalidateOptionsMenu()
                    }
                }
                true
            }
            R.id.delete -> {
                val size = explorerAdapter.selectedItems.size
                val dialog = CustomAlertDialogBuilder(this, theme)
                dialog.setTitle(R.string.warning)
                dialog.setPositiveButton(R.string.ok) { _, _ ->
                    val items = explorerAdapter.selectedItems.map { i -> explorerElements[i] }
                    activityScope.launch {
                        fileOperationService.removeElements(volumeId, items)?.let { failedItem ->
                            CustomAlertDialogBuilder(this@ExplorerActivity, theme)
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.remove_failed, failedItem))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        }
                        setCurrentPath(currentDirectoryPath) //refresh
                    }
                    unselectAll()
                }
                dialog.setNegativeButton(R.string.cancel, null)
                if (size > 1) {
                    dialog.setMessage(getString(R.string.multiple_delete_confirm, explorerAdapter.selectedItems.size.toString()))
                } else {
                    dialog.setMessage(getString(
                        R.string.single_delete_confirm,
                        explorerAdapter.explorerElements[explorerAdapter.selectedItems.first()].name
                    ))
                }
                dialog.show()
                true
            }
            R.id.share -> {
                val files = explorerAdapter.selectedItems.map { i ->
                    explorerElements[i].let {
                        Pair(it.fullPath, it.stat.size)
                    }
                }
                app.isExporting = true
                object : LoadingTask<Pair<Intent?, Int?>>(this, theme, R.string.loading_msg_export) {
                    override suspend fun doTask(): Pair<Intent?, Int?> {
                        return fileShare.share(files, volumeId)
                    }
                }.startTask(lifecycleScope) { (intent, error) ->
                    if (intent == null) {
                        onExportFailed(error!!)
                    } else {
                        app.isStartingExternalApp = true
                        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
                    }
                    app.isExporting = false
                }
                unselectAll()
                true
            }
            R.id.decrypt -> {
                app.isStartingExternalApp = true
                pickExportDirectory.launch(null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Ask the user what to do if an item would overwrite another item in case of a move.
     *
     * All [OperationFile] must have a non-null [dstPath][OperationFile.dstPath].
     */
    private fun checkMoveOverwrite(items: List<OperationFile>, callback: (List<OperationFile>?) -> Unit) {
        for (item in items) {
            if (encryptedVolume.pathExists(item.dstPath!!) && !item.overwriteConfirmed) {
                CustomAlertDialogBuilder(this, theme)
                    .setTitle(R.string.warning)
                    .setMessage(
                        getString(
                            if (item.isDirectory) {
                                R.string.dir_overwrite_question
                            } else {
                                R.string.file_overwrite_question
                            },
                            item.dstPath!!
                        )
                    )
                    .setPositiveButton(R.string.yes) {_, _ ->
                        item.overwriteConfirmed = true
                        checkMoveOverwrite(items, callback)
                    }
                    .setNegativeButton(R.string.no) { _, _ ->
                        with(EditTextDialog(this, R.string.enter_new_name) {
                            item.dstPath = PathUtils.pathJoin(PathUtils.getParentPath(item.dstPath!!), it)
                            checkMoveOverwrite(items, callback)
                        }) {
                            setSelectedText(item.name)
                            show()
                        }
                    }
                    .show()
                return
            }
        }
        callback(items)
    }

    /**
     * Check for destination overwriting in case of a move operation.
     *
     * If the user decides to merge the content of a folder, the function recursively tests all
     * children of the source folder to see if they will overwrite.
     *
     * The items to be moved are stored in [toMove]. We also need to keep track of the merged
     * folders to delete them after the move. These folders are stored in [toClean].
     */
    private fun prepareFilesForMove(
        items: List<OperationFile>,
        toMove: ArrayList<OperationFile>,
        toClean: ArrayList<String>,
        onReady: () -> Unit
    ) {
        checkMoveOverwrite(items) { checkedItems ->
            checkedItems?.let {
                for (item in checkedItems) {
                    if (!item.overwriteConfirmed || !item.isDirectory) {
                        toMove.add(item)
                    }
                }
                val toCheck = mutableListOf<OperationFile>()
                for (item in checkedItems) {
                    if (item.overwriteConfirmed && item.isDirectory) {
                        val children = encryptedVolume.readDir(item.srcPath)
                        children?.map {
                            OperationFile(it.fullPath, it.stat.type, PathUtils.pathJoin(item.dstPath!!, it.name))
                        }?.let { toCheck.addAll(it) }
                        toClean.add(item.srcPath)
                    }
                }
                if (toCheck.isEmpty()) {
                    onReady()
                } else {
                    prepareFilesForMove(toCheck, toMove, toClean, onReady)
                }
            }
        }
    }

    private fun cancelItemAction() {
        if (currentItemAction != ItemsActions.NONE){
            currentItemAction = ItemsActions.NONE
            itemsToProcess.clear()
        }
    }
}

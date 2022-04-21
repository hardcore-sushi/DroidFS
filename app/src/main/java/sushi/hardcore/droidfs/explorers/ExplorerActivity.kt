package sushi.hardcore.droidfs.explorers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.CameraActivity
import sushi.hardcore.droidfs.GocryptfsVolume
import sushi.hardcore.droidfs.MainActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.adapters.IconTextDialogAdapter
import sushi.hardcore.droidfs.content_providers.ExternalProvider
import sushi.hardcore.droidfs.databinding.ActivityExplorerBinding
import sushi.hardcore.droidfs.file_operations.OperationFile
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog
import java.io.File

class ExplorerActivity : BaseExplorerActivity() {
    companion object {
        private enum class ItemsActions {NONE, COPY, MOVE}
    }

    private var usf_decrypt = false
    private var usf_share = false
    private var currentItemAction = ItemsActions.NONE
    private val itemsToProcess = ArrayList<OperationFile>()
    private lateinit var binding: ActivityExplorerBinding
    private val pickFromOtherVolumes = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { resultIntent ->
                val remoteSessionID = resultIntent.getIntExtra("sessionID", -1)
                val remoteGocryptfsVolume = GocryptfsVolume(applicationContext, remoteSessionID)
                val path = resultIntent.getStringExtra("path")
                val operationFiles = ArrayList<OperationFile>()
                if (path == null){ //multiples elements
                    val paths = resultIntent.getStringArrayListExtra("paths")
                    val types = resultIntent.getIntegerArrayListExtra("types")
                    if (types != null && paths != null){
                        for (i in paths.indices) {
                            operationFiles.add(
                                OperationFile.fromExplorerElement(
                                    ExplorerElement(File(paths[i]).name, types[i].toShort(), parentPath = PathUtils.getParentPath(paths[i]))
                                )
                            )
                            if (types[i] == 0){ //directory
                                remoteGocryptfsVolume.recursiveMapFiles(paths[i]).forEach {
                                    operationFiles.add(OperationFile.fromExplorerElement(it))
                                }
                            }
                        }
                    }
                } else {
                    operationFiles.add(
                        OperationFile.fromExplorerElement(
                            ExplorerElement(File(path).name, 1, parentPath = PathUtils.getParentPath(path))
                        )
                    )
                }
                if (operationFiles.size > 0){
                    checkPathOverwrite(operationFiles, currentDirectoryPath) { items ->
                        if (items == null) {
                            remoteGocryptfsVolume.close()
                        } else {
                            lifecycleScope.launch {
                                val failedItem = fileOperationService.copyElements(items, remoteGocryptfsVolume)
                                if (failedItem == null) {
                                    Toast.makeText(this@ExplorerActivity, R.string.success_import, Toast.LENGTH_SHORT).show()
                                } else {
                                    CustomAlertDialogBuilder(this@ExplorerActivity, themeValue)
                                        .setTitle(R.string.error)
                                        .setMessage(getString(R.string.import_failed, failedItem))
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                                }
                                setCurrentPath(currentDirectoryPath)
                                remoteGocryptfsVolume.close()
                            }
                        }
                    }
                } else {
                    remoteGocryptfsVolume.close()
                }
            }
        }
    }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null) {
            importFilesFromUris(uris){ failedItem ->
                onImportComplete(failedItem, uris)
            }
        }
    }
    private val pickExportDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val result = fileOperationService.exportFiles(uri, explorerAdapter.selectedItems.map { i -> explorerElements[i] })
                if (!result.cancelled) {
                    if (result.failedItem == null) {
                        Toast.makeText(this@ExplorerActivity, R.string.success_export, Toast.LENGTH_SHORT).show()
                    } else {
                        CustomAlertDialogBuilder(this@ExplorerActivity, themeValue)
                            .setTitle(R.string.error)
                            .setMessage(getString(R.string.export_failed, result.failedItem))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
        }
        unselectAll()
    }
    private val pickImportDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { rootUri ->
        rootUri?.let {
            val tree = DocumentFile.fromTreeUri(this, it)!! //non-null after Lollipop
            val operation = OperationFile.fromExplorerElement(ExplorerElement(tree.name!!, 0, parentPath = currentDirectoryPath))
            checkPathOverwrite(arrayListOf(operation), currentDirectoryPath) { checkedOperation ->
                checkedOperation?.let {
                    lifecycleScope.launch {
                        val result = fileOperationService.importDirectory(checkedOperation[0].dstPath!!, tree)
                        if (result.taskResult.cancelled) {
                            setCurrentPath(currentDirectoryPath)
                        } else {
                            onImportComplete(result.taskResult.failedItem, result.uris, tree)
                        }
                    }
                }
            }
        }
    }

    private fun onImportComplete(failedItem: String?, urisToWipe: List<Uri>, rootFile: DocumentFile? = null) {
        if (failedItem == null){
            CustomAlertDialogBuilder(this, themeValue)
                .setTitle(R.string.success_import)
                .setMessage("""
                                ${getString(R.string.success_import_msg)}
                                ${getString(R.string.ask_for_wipe)}
                                """.trimIndent())
                .setPositiveButton(R.string.yes) { _, _ ->
                    lifecycleScope.launch {
                        val errorMsg = fileOperationService.wipeUris(urisToWipe, rootFile)
                        if (errorMsg == null) {
                            Toast.makeText(this@ExplorerActivity, R.string.wipe_successful, Toast.LENGTH_SHORT).show()
                        } else {
                            CustomAlertDialogBuilder(this@ExplorerActivity, themeValue)
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.wipe_failed, errorMsg))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        }
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            CustomAlertDialogBuilder(this, themeValue)
                .setTitle(R.string.error)
                .setMessage(getString(R.string.import_failed, failedItem))
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        setCurrentPath(currentDirectoryPath)
    }

    override fun init() {
        binding = ActivityExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.fab.setOnClickListener {
            if (currentItemAction != ItemsActions.NONE){
                openDialogCreateFolder()
            } else {
                val adapter = IconTextDialogAdapter(this)
                adapter.items = listOf(
                    listOf("importFromOtherVolumes", R.string.import_from_other_volume, R.drawable.icon_transfert),
                    listOf("importFiles", R.string.import_files, R.drawable.icon_encrypt),
                    listOf("importFolder", R.string.import_folder, R.drawable.icon_import_folder),
                    listOf("createFile", R.string.new_file, R.drawable.icon_file_unknown),
                    listOf("createFolder", R.string.mkdir, R.drawable.icon_create_new_folder),
                    listOf("camera", R.string.camera, R.drawable.icon_photo)
                )
                CustomAlertDialogBuilder(this, themeValue)
                    .setSingleChoiceItems(adapter, -1){ thisDialog, which ->
                        when (adapter.getItem(which)){
                            "importFromOtherVolumes" -> {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.action = "pick"
                                intent.putExtra("sessionID", gocryptfsVolume.sessionID)
                                isStartingActivity = true
                                pickFromOtherVolumes.launch(intent)
                            }
                            "importFiles" -> {
                                isStartingActivity = true
                                pickFiles.launch(arrayOf("*/*"))
                            }
                            "importFolder" -> {
                                isStartingActivity = true
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
                                intent.putExtra("sessionID", gocryptfsVolume.sessionID)
                                isStartingActivity = true
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
            val handleID = gocryptfsVolume.openWriteMode(filePath) //don't check overwrite because openWriteMode open in read-write (doesn't erase content)
            if (handleID == -1) {
                CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.error)
                        .setMessage(R.string.file_creation_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show()
            } else {
                gocryptfsVolume.closeFile(handleID)
                setCurrentPath(currentDirectoryPath)
                invalidateOptionsMenu()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer, menu)
        if (currentItemAction != ItemsActions.NONE) {
            menu.findItem(R.id.validate).isVisible = true
            menu.findItem(R.id.close).isVisible = false
        } else {
            handleMenuItems(menu)
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
        return true
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
                    if (explorerElements[i].isDirectory){
                        gocryptfsVolume.recursiveMapFiles(explorerElements[i].fullPath).forEach {
                            itemsToProcess.add(OperationFile.fromExplorerElement(it))
                        }
                    }
                }
                currentItemAction = ItemsActions.COPY
                unselectAll()
                true
            }
            R.id.validate -> {
                if (currentItemAction == ItemsActions.COPY){
                    checkPathOverwrite(itemsToProcess, currentDirectoryPath){ items ->
                        items?.let {
                            lifecycleScope.launch {
                                val failedItem = fileOperationService.copyElements(it.toMutableList() as ArrayList<OperationFile>)
                                if (!isFinishing) {
                                    if (failedItem == null) {
                                        Toast.makeText(this@ExplorerActivity, R.string.copy_success, Toast.LENGTH_SHORT).show()
                                    } else {
                                        CustomAlertDialogBuilder(this@ExplorerActivity, themeValue)
                                            .setTitle(R.string.error)
                                            .setMessage(getString(R.string.copy_failed, failedItem))
                                            .setPositiveButton(R.string.ok, null)
                                            .show()
                                    }
                                    setCurrentPath(currentDirectoryPath)
                                }
                            }
                        }
                        cancelItemAction()
                        invalidateOptionsMenu()
                    }
                } else if (currentItemAction == ItemsActions.MOVE){
                    itemsToProcess.forEach {
                        it.dstPath = PathUtils.pathJoin(currentDirectoryPath, it.explorerElement.name)
                        it.overwriteConfirmed = false // reset the field in case of a previous cancelled move
                    }
                    val toMove = ArrayList<OperationFile>(itemsToProcess.size)
                    val toClean = ArrayList<ExplorerElement>()
                    prepareFilesForMove(
                        itemsToProcess,
                        toMove,
                        toClean,
                    ) {
                        lifecycleScope.launch {
                            val failedItem = fileOperationService.moveElements(toMove, toClean)
                            if (failedItem == null) {
                                Toast.makeText(this@ExplorerActivity, R.string.move_success, Toast.LENGTH_SHORT).show()
                            } else {
                                CustomAlertDialogBuilder(this@ExplorerActivity, themeValue)
                                    .setTitle(R.string.error)
                                    .setMessage(getString(R.string.move_failed, failedItem))
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
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
                val dialog = CustomAlertDialogBuilder(this, themeValue)
                dialog.setTitle(R.string.warning)
                dialog.setPositiveButton(R.string.ok) { _, _ -> removeSelectedItems() }
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
                val paths: MutableList<String> = ArrayList()
                for (i in explorerAdapter.selectedItems) {
                    paths.add(explorerElements[i].fullPath)
                }
                isStartingActivity = true
                ExternalProvider.share(this, themeValue, gocryptfsVolume, paths)
                unselectAll()
                true
            }
            R.id.decrypt -> {
                isStartingActivity = true
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
            if (gocryptfsVolume.pathExists(item.dstPath!!) && !item.overwriteConfirmed) {
                CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.warning)
                    .setMessage(
                        getString(
                            if (item.explorerElement.isDirectory) {
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
                            setSelectedText(item.explorerElement.name)
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
        toClean: ArrayList<ExplorerElement>,
        onReady: () -> Unit
    ) {
        checkMoveOverwrite(items) { checkedItems ->
            checkedItems?.let {
                for (item in checkedItems) {
                    if (!item.overwriteConfirmed || !item.explorerElement.isDirectory) {
                        toMove.add(item)
                    }
                }
                val toCheck = mutableListOf<OperationFile>()
                for (item in checkedItems) {
                    if (item.overwriteConfirmed && item.explorerElement.isDirectory) {
                        val children = gocryptfsVolume.listDir(item.explorerElement.fullPath)
                        toCheck.addAll(children.map {
                            OperationFile(it, PathUtils.pathJoin(item.dstPath!!, it.name))
                        })
                        toClean.add(item.explorerElement)
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

    override fun onBackPressed() {
        if (currentItemAction != ItemsActions.NONE) {
            cancelItemAction()
            invalidateOptionsMenu()
        } else {
            super.onBackPressed()
        }
    }

    private fun removeSelectedItems() {
        var failedItem: String? = null
        for (i in explorerAdapter.selectedItems) {
            val element = explorerAdapter.explorerElements[i]
            val fullPath = PathUtils.pathJoin(currentDirectoryPath, element.name)
            if (element.isDirectory) {
                val result = gocryptfsVolume.recursiveRemoveDirectory(fullPath)
                result?.let{ failedItem = it }
            } else {
                if (!gocryptfsVolume.removeFile(fullPath)) {
                    failedItem = fullPath
                }
            }
            if (failedItem != null) {
                CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.remove_failed, failedItem))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                break
            }
        }
        setCurrentPath(currentDirectoryPath) //refresh
    }
}

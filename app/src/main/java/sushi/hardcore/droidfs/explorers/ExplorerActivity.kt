package sushi.hardcore.droidfs.explorers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import sushi.hardcore.droidfs.CameraActivity
import sushi.hardcore.droidfs.OpenActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.adapters.IconTextDialogAdapter
import sushi.hardcore.droidfs.file_operations.OperationFile
import sushi.hardcore.droidfs.util.ExternalProvider
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File

class ExplorerActivity : BaseExplorerActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
        private const val PICK_FILES_REQUEST_CODE = 2
        private const val PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE = 3
        private enum class ItemsActions {NONE, COPY, MOVE}
    }
    private var usf_decrypt = false
    private var usf_share = false
    private var currentItemAction = ItemsActions.NONE
    private val itemsToProcess = ArrayList<OperationFile>()
    override fun init() {
        setContentView(R.layout.activity_explorer)
        usf_decrypt = sharedPrefs.getBoolean("usf_decrypt", false)
        usf_share = sharedPrefs.getBoolean("usf_share", false)
    }

    override fun onExplorerItemLongClick(position: Int) {
        cancelItemAction()
        explorerAdapter.onItemLongClick(position)
        invalidateOptionsMenu()
    }

    private fun createNewFile(fileName: String){
        if (fileName.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            val handleID = gocryptfsVolume.openWriteMode(fileName) //don't check overwrite because openWriteMode open in read-write (doesn't erase content)
            if (handleID == -1) {
                ColoredAlertDialogBuilder(this)
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

    fun onClickFAB(view: View) {
        if (currentItemAction != ItemsActions.NONE){
            openDialogCreateFolder()
        } else {
            val adapter = IconTextDialogAdapter(this)
            adapter.items = listOf(
                listOf("importFromOtherVolumes", R.string.import_from_other_volume, R.drawable.icon_transfert),
                listOf("importFiles", R.string.import_files, R.drawable.icon_encrypt),
                listOf("createFile", R.string.new_file, R.drawable.icon_file_unknown),
                listOf("createFolder", R.string.mkdir, R.drawable.icon_folder),
                listOf("takePhoto", R.string.take_photo, R.drawable.icon_camera)
            )
            ColoredAlertDialogBuilder(this)
                .setSingleChoiceItems(adapter, -1){ thisDialog, which ->
                    when (adapter.getItem(which)){
                        "importFromOtherVolumes" -> {
                            val intent = Intent(this, OpenActivity::class.java)
                            intent.action = "pick"
                            intent.putExtra("sessionID", gocryptfsVolume.sessionID)
                            isStartingActivity = true
                            startActivityForResult(intent, PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE)
                        }
                        "importFiles" -> {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            intent.type = "*/*"
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            intent.addCategory(Intent.CATEGORY_OPENABLE)
                            isStartingActivity = true
                            startActivityForResult(intent, PICK_FILES_REQUEST_CODE)
                        }
                        "createFile" -> {
                            val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
                            val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
                            val dialog = ColoredAlertDialogBuilder(this)
                                .setView(dialogEditTextView)
                                .setTitle(getString(R.string.enter_file_name))
                                .setPositiveButton(R.string.ok) { _, _ ->
                                    val fileName = dialogEditText.text.toString()
                                    createNewFile(fileName)
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .create()
                            dialogEditText.setOnEditorActionListener { _, _, _ ->
                                val fileName = dialogEditText.text.toString()
                                dialog.dismiss()
                                createNewFile(fileName)
                                true
                            }
                            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                            dialog.show()
                        }
                        "createFolder" -> {
                            openDialogCreateFolder()
                        }
                        "takePhoto" -> {
                            val intent = Intent(this, CameraActivity::class.java)
                            intent.putExtra("path", currentDirectoryPath)
                            intent.putExtra("sessionID", gocryptfsVolume.sessionID)
                            isStartingActivity = true
                            startActivity(intent)
                        }
                    }
                    thisDialog.dismiss()
                }
                .setTitle(getString(R.string.fab_dialog_title))
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILES_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uris: MutableList<Uri> = ArrayList()
                val singleUri = data.data
                if (singleUri == null) { //multiples choices
                    val clipData = data.clipData
                    if (clipData != null){
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    }
                } else {
                    uris.add(singleUri)
                }
                importFilesFromUris(uris){ failedItem ->
                    if (failedItem == null){
                        ColoredAlertDialogBuilder(this)
                                .setTitle(R.string.success_import)
                                .setMessage("""
                                ${getString(R.string.success_import_msg)}
                                ${getString(R.string.ask_for_wipe)}
                                """.trimIndent())
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    fileOperationService.wipeUris(uris) { errorMsg ->
                                        runOnUiThread {
                                            if (errorMsg == null){
                                                ColoredAlertDialogBuilder(this)
                                                        .setTitle(R.string.wipe_successful)
                                                        .setMessage(R.string.wipe_success_msg)
                                                        .setPositiveButton(R.string.ok, null)
                                                        .show()
                                            } else {
                                                ColoredAlertDialogBuilder(this)
                                                        .setTitle(R.string.error)
                                                        .setMessage(getString(R.string.wipe_failed, errorMsg))
                                                        .setPositiveButton(R.string.ok, null)
                                                        .show()
                                            }
                                        }

                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                    } else {
                        ColoredAlertDialogBuilder(this)
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.import_failed, failedItem))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                    }
                    setCurrentPath(currentDirectoryPath)
                }
            }
        } else if (requestCode == PICK_DIRECTORY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                data.data?.let { uri ->
                    fileOperationService.exportFiles(uri, explorerAdapter.selectedItems.map { i -> explorerElements[i] }){ failedItem ->
                        runOnUiThread {
                            if (failedItem == null){
                                ColoredAlertDialogBuilder(this)
                                        .setTitle(R.string.success_export)
                                        .setMessage(R.string.success_export_msg)
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                            } else {
                                ColoredAlertDialogBuilder(this)
                                        .setTitle(R.string.error)
                                        .setMessage(getString(R.string.export_failed, failedItem))
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                            }
                        }
                    }
                    unselectAll()
                }
            }
        } else if (requestCode == PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val remoteSessionID = data.getIntExtra("sessionID", -1)
                val remoteGocryptfsVolume = GocryptfsVolume(remoteSessionID)
                val path = data.getStringExtra("path")
                val operationFiles = ArrayList<OperationFile>()
                if (path == null){ //multiples elements
                    val paths = data.getStringArrayListExtra("paths")
                    val types = data.getIntegerArrayListExtra("types")
                    if (types != null && paths != null){
                        for (i in paths.indices) {
                            operationFiles.add(
                                    OperationFile.fromExplorerElement(
                                            ExplorerElement(File(paths[i]).name, types[i].toShort(), -1, -1, PathUtils.getParentPath(paths[i]))
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
                                    ExplorerElement(File(path).name, 1, -1, -1, PathUtils.getParentPath(path))
                            )
                    )
                }
                if (operationFiles.size > 0){
                    checkPathOverwrite(operationFiles, currentDirectoryPath) { items ->
                        if (items == null) {
                            remoteGocryptfsVolume.close()
                        } else {
                            fileOperationService.copyElements(items, remoteGocryptfsVolume){ failedItem ->
                                runOnUiThread {
                                    if (failedItem == null){
                                        ColoredAlertDialogBuilder(this)
                                                .setTitle(R.string.success_import)
                                                .setMessage(R.string.success_import_msg)
                                                .setPositiveButton(R.string.ok, null)
                                                .show()
                                    } else {
                                        ColoredAlertDialogBuilder(this)
                                                .setTitle(R.string.error)
                                                .setMessage(getString(R.string.import_failed, failedItem))
                                                .setPositiveButton(R.string.ok, null)
                                                .show()
                                    }
                                    setCurrentPath(currentDirectoryPath)
                                }
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
                            fileOperationService.copyElements(it.toMutableList() as ArrayList<OperationFile>){ failedItem ->
                                runOnUiThread {
                                    if (failedItem != null){
                                        ColoredAlertDialogBuilder(this)
                                                .setTitle(R.string.error)
                                                .setMessage(getString(R.string.copy_failed, failedItem))
                                                .setPositiveButton(R.string.ok, null)
                                                .show()
                                    } else {
                                        Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show()
                                    }
                                    setCurrentPath(currentDirectoryPath)
                                }
                            }
                        }
                        cancelItemAction()
                        unselectAll()
                    }
                } else if (currentItemAction == ItemsActions.MOVE){
                    mapFileForMove(itemsToProcess, itemsToProcess[0].explorerElement.parentPath)
                    checkPathOverwrite(itemsToProcess, currentDirectoryPath){ items ->
                        items?.let {
                            fileOperationService.moveElements(it.toMutableList() as ArrayList<OperationFile>){ failedItem ->
                                runOnUiThread {
                                    if (failedItem != null){
                                        ColoredAlertDialogBuilder(this)
                                                .setTitle(R.string.error)
                                                .setMessage(getString(R.string.move_failed, failedItem))
                                                .setPositiveButton(R.string.ok, null)
                                                .show()
                                    } else {
                                        Toast.makeText(this, R.string.move_success, Toast.LENGTH_SHORT).show()
                                    }
                                    setCurrentPath(currentDirectoryPath)
                                }
                            }
                        }
                        cancelItemAction()
                        unselectAll()
                    }
                }
                true
            }
            R.id.delete -> {
                val size = explorerAdapter.selectedItems.size
                val dialog = ColoredAlertDialogBuilder(this)
                dialog.setTitle(R.string.warning)
                dialog.setPositiveButton(R.string.ok) { _, _ -> removeSelectedItems() }
                dialog.setNegativeButton(R.string.cancel, null)
                if (size > 1) {
                    dialog.setMessage(getString(R.string.multiple_delete_confirm, explorerAdapter.selectedItems.size.toString()))
                } else {
                    dialog.setMessage(getString(R.string.single_delete_confirm, explorerAdapter.getItem(explorerAdapter.selectedItems[0]).name))
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
                ExternalProvider.share(this, gocryptfsVolume, paths)
                unselectAll()
                true
            }
            R.id.decrypt -> {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                isStartingActivity = true
                startActivityForResult(i, PICK_DIRECTORY_REQUEST_CODE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun mapFileForMove(items: ArrayList<OperationFile>, srcDirectoryPath: String): ArrayList<OperationFile> {
        val newItems = ArrayList<OperationFile>()
        items.forEach {
            if (it.explorerElement.isDirectory){
                if (gocryptfsVolume.pathExists(PathUtils.pathJoin(currentDirectoryPath, PathUtils.getRelativePath(srcDirectoryPath, it.explorerElement.fullPath)))){
                    newItems.addAll(
                        mapFileForMove(
                            gocryptfsVolume.listDir(it.explorerElement.fullPath).map { e -> OperationFile.fromExplorerElement(e) } as ArrayList<OperationFile>,
                            srcDirectoryPath
                        )
                    )
                }
            }
        }
        items.addAll(newItems)
        return items
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
            val element = explorerAdapter.getItem(i)
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
                ColoredAlertDialogBuilder(this)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.remove_failed, failedItem))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                break
            }
        }
        unselectAll()
        setCurrentPath(currentDirectoryPath) //refresh
    }
}

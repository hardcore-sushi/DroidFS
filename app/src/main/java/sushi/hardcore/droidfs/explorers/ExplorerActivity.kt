package sushi.hardcore.droidfs.explorers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.CameraActivity
import sushi.hardcore.droidfs.OpenActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.adapters.IconTextDialogAdapter
import sushi.hardcore.droidfs.util.*
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import kotlin.collections.ArrayList

class ExplorerActivity : BaseExplorerActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private val PICK_FILES_REQUEST_CODE = 2
    private val PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE = 3
    private var usf_decrypt = false
    private var usf_share = false
    private var modeSelectLocation = false
    private val filesToCopy = ArrayList<ExplorerElement>()
    override fun init() {
        setContentView(R.layout.activity_explorer)
        usf_decrypt = sharedPrefs.getBoolean("usf_decrypt", false)
        usf_share = sharedPrefs.getBoolean("usf_share", false)
    }

    override fun onExplorerItemLongClick(position: Int) {
        cancelCopy()
        explorerAdapter.onItemLongClick(position)
        invalidateOptionsMenu()
    }

    private fun createNewFile(fileName: String){
        if (fileName.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            checkFileOverwrite(PathUtils.path_join(currentDirectoryPath, fileName))?.let {
                val handleID = gocryptfsVolume.openWriteMode(it)
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
    }

    fun onClickFAB(view: View) {
        if (modeSelectLocation){
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
                            startActivityForResult(intent, PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE)
                        }
                        "importFiles" -> {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                            intent.type = "*/*"
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            intent.addCategory(Intent.CATEGORY_OPENABLE)
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
                            startActivity(intent)
                        }
                    }
                    thisDialog.dismiss()
                }
                .setTitle(getString(R.string.fab_dialog_title))
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILES_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                object : LoadingTask(this, R.string.loading_msg_import){
                    override fun doTask(activity: AppCompatActivity) {
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
                        Looper.prepare()
                        var success = false
                        for (uri in uris) {
                            val dstPath = checkFileOverwrite(PathUtils.path_join(currentDirectoryPath, PathUtils.getFilenameFromURI(activity, uri)))
                            if (dstPath == null){
                                break
                            } else {
                                contentResolver.openInputStream(uri)?.let {
                                    success = gocryptfsVolume.importFile(it, dstPath)
                                }
                                if (!success) {
                                    stopTask {
                                        ColoredAlertDialogBuilder(activity)
                                            .setTitle(R.string.error)
                                            .setMessage(getString(R.string.import_failed, uri))
                                            .setPositiveButton(R.string.ok, null)
                                            .show()
                                    }
                                    break
                                }
                            }
                        }
                        if (success) {
                            stopTask {
                                ColoredAlertDialogBuilder(activity)
                                    .setTitle(R.string.success_import)
                                    .setMessage("""
                                ${getString(R.string.success_import_msg)}
                                ${getString(R.string.ask_for_wipe)}
                                """.trimIndent())
                                    .setPositiveButton(R.string.yes) { _, _ ->
                                        object : LoadingTask(activity, R.string.loading_msg_wipe){
                                            override fun doTask(activity: AppCompatActivity) {
                                                success = true
                                                for (uri in uris) {
                                                    val errorMsg = Wiper.wipe(activity, uri)
                                                    if (errorMsg != null) {
                                                        stopTask {
                                                            ColoredAlertDialogBuilder(activity)
                                                                .setTitle(R.string.error)
                                                                .setMessage(getString(R.string.wipe_failed, errorMsg))
                                                                .setPositiveButton(R.string.ok, null)
                                                                .show()
                                                        }
                                                        success = false
                                                        break
                                                    }
                                                }
                                                if (success) {
                                                    stopTask {
                                                        ColoredAlertDialogBuilder(activity)
                                                            .setTitle(R.string.wipe_successful)
                                                            .setMessage(R.string.wipe_success_msg)
                                                            .setPositiveButton(R.string.ok, null)
                                                            .show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton(getString(R.string.no), null)
                                    .show()
                            }
                        }
                    }
                    override fun doFinally(activity: AppCompatActivity){
                        setCurrentPath(currentDirectoryPath)
                    }
                }
            }
        } else if (requestCode == PICK_DIRECTORY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                object : LoadingTask(this, R.string.loading_msg_export){
                    override fun doTask(activity: AppCompatActivity) {
                        val uri = data.data
                        val outputDir = PathUtils.getFullPathFromTreeUri(uri, activity)
                        var failedItem: String? = null
                        for (i in explorerAdapter.selectedItems) {
                            val element = explorerAdapter.getItem(i)
                            val fullPath = PathUtils.path_join(currentDirectoryPath, element.name)
                            failedItem = if (element.isDirectory) {
                                recursiveExportDirectory(fullPath, outputDir)
                            } else {
                                if (gocryptfsVolume.exportFile(fullPath, PathUtils.path_join(outputDir, element.name))) null else fullPath
                            }
                            if (failedItem != null) {
                                stopTask {
                                    ColoredAlertDialogBuilder(activity)
                                        .setTitle(R.string.error)
                                        .setMessage(getString(R.string.export_failed, failedItem))
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                                }
                                break
                            }
                        }
                        if (failedItem == null) {
                            stopTask {
                                ColoredAlertDialogBuilder(activity)
                                    .setTitle(R.string.success_export)
                                    .setMessage(R.string.success_export_msg)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    }
                    override fun doFinally(activity: AppCompatActivity) {
                        unselectAll()
                    }
                }
            }
        } else if (requestCode == PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                object : LoadingTask(this, R.string.loading_msg_import){
                    override fun doTask(activity: AppCompatActivity) {
                        val remoteSessionID = data.getIntExtra("sessionID", -1)
                        val remoteGocryptfsVolume = GocryptfsVolume(remoteSessionID)
                        val path = data.getStringExtra("path")
                        var failedItem: String? = null
                        Looper.prepare()
                        if (path == null) {
                            val paths = data.getStringArrayListExtra("paths")
                            val types = data.getIntegerArrayListExtra("types")
                            if (types != null && paths != null){
                                for (i in paths.indices) {
                                    failedItem = if (types[i] == 0) { //directory
                                        recursiveImportDirectoryFromOtherVolume(remoteGocryptfsVolume, paths[i], currentDirectoryPath)
                                    } else {
                                        safeImportFileFromOtherVolume(remoteGocryptfsVolume, paths[i], PathUtils.path_join(currentDirectoryPath, File(paths[i]).name))
                                    }
                                    if (failedItem != null) {
                                        break
                                    }
                                }
                            }
                        } else {
                            failedItem = safeImportFileFromOtherVolume(remoteGocryptfsVolume, path, PathUtils.path_join(currentDirectoryPath, File(path).name))
                        }
                        if (failedItem == null) {
                            stopTask {
                                ColoredAlertDialogBuilder(activity)
                                    .setTitle(R.string.success_import)
                                    .setMessage(R.string.success_import_msg)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        } else if (failedItem!!.isNotEmpty()){
                            stopTask {
                                ColoredAlertDialogBuilder(activity)
                                    .setTitle(R.string.error)
                                    .setMessage(getString(R.string.import_failed, failedItem))
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                        remoteGocryptfsVolume.close()
                    }
                    override fun doFinally(activity: AppCompatActivity) {
                        setCurrentPath(currentDirectoryPath)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer, menu)
        if (modeSelectLocation) {
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
                cancelCopy()
                super.onOptionsItemSelected(item)
            }
            R.id.select_all -> {
                explorerAdapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.copy -> {
                for (i in explorerAdapter.selectedItems){
                    filesToCopy.add(explorerElements[i])
                }
                modeSelectLocation = true
                unselectAll()
                true
            }
            R.id.validate -> {
                object : LoadingTask(this, R.string.loading_msg_copy){
                    override fun doTask(activity: AppCompatActivity) {
                        var failedItem: String? = null
                        Looper.prepare()
                        for (element in filesToCopy) {
                            failedItem = if (element.isDirectory) {
                                recursiveCopyDirectory(element.fullPath, currentDirectoryPath)
                            } else {
                                val dstPath = checkFileOverwrite(PathUtils.path_join(currentDirectoryPath, element.name))
                                if (dstPath == null){
                                    ""
                                } else {
                                    if (copyFile(element.fullPath, dstPath)) null else element.fullPath
                                }
                            }
                            if (failedItem != null && failedItem.isNotEmpty()) {
                                stopTask {
                                    ColoredAlertDialogBuilder(activity)
                                        .setTitle(R.string.error)
                                        .setMessage(getString(R.string.copy_failed, failedItem))
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                                }
                                break
                            }
                        }
                        if (failedItem == null) {
                            stopTask {
                                ColoredAlertDialogBuilder(activity)
                                    .setTitle(getString(R.string.copy_success))
                                    .setMessage(getString(R.string.copy_success_msg))
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    }
                    override fun doFinally(activity: AppCompatActivity) {
                        cancelCopy()
                        unselectAll()
                        setCurrentPath(currentDirectoryPath)
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
                ExternalProvider.share(this, gocryptfsVolume, paths)
                unselectAll()
                true
            }
            R.id.decrypt -> {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(i, PICK_DIRECTORY_REQUEST_CODE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun cancelCopy() {
        if (modeSelectLocation){
            modeSelectLocation = false
            filesToCopy.clear()
        }
    }

    override fun onBackPressed() {
        if (modeSelectLocation) {
            cancelCopy()
            invalidateOptionsMenu()
        } else {
            super.onBackPressed()
        }
    }

    private fun copyFile(srcPath: String, dstPath: String): Boolean {
        var success = true
        val originalHandleId = gocryptfsVolume.openReadMode(srcPath)
        if (originalHandleId != -1){
            val newHandleId = gocryptfsVolume.openWriteMode(dstPath)
            if (newHandleId != -1){
                var offset: Long = 0
                val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
                var length: Int
                while (gocryptfsVolume.readFile(originalHandleId, offset, ioBuffer).also { length = it } > 0) {
                    val written = gocryptfsVolume.writeFile(newHandleId, offset, ioBuffer, length).toLong()
                    if (written == length.toLong()) {
                        offset += written
                    } else {
                        success = false
                        break
                    }
                }
                gocryptfsVolume.closeFile(newHandleId)
            } else {
                success = false
            }
            gocryptfsVolume.closeFile(originalHandleId)
        } else {
            success = false
        }
        return success
    }

    private fun recursiveCopyDirectory(srcDirectoryPath: String, outputPath: String): String? {
        val mappedElements = gocryptfsVolume.recursiveMapFiles(srcDirectoryPath)
        val dstDirectoryPath = PathUtils.path_join(outputPath, File(srcDirectoryPath).name)
        if (!gocryptfsVolume.pathExists(dstDirectoryPath)) {
            if (!gocryptfsVolume.mkdir(dstDirectoryPath)) {
                return dstDirectoryPath
            }
        }
        for (e in mappedElements) {
            val dstPath = PathUtils.path_join(dstDirectoryPath, PathUtils.getRelativePath(srcDirectoryPath, e.fullPath))
            if (e.isDirectory) {
                if (!gocryptfsVolume.mkdir(dstPath)){
                    return e.fullPath
                }
            } else {
                val checkedDstPath = checkFileOverwrite(dstPath)
                if (checkedDstPath == null){
                    return ""
                } else {
                    if (!copyFile(e.fullPath, dstPath)) {
                        return e.fullPath
                    }
                }
            }
        }
        return null
    }

    private fun importFileFromOtherVolume(remoteGocryptfsVolume: GocryptfsVolume, srcPath: String, dstPath: String): Boolean {
        var success = true
        val srcHandleID = remoteGocryptfsVolume.openReadMode(srcPath)
        if (srcHandleID != -1) {
            val dstHandleID = gocryptfsVolume.openWriteMode(dstPath)
            if (dstHandleID != -1) {
                var length: Int
                val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
                var offset: Long = 0
                while (remoteGocryptfsVolume.readFile(srcHandleID, offset, ioBuffer)
                        .also { length = it } > 0
                ) {
                    val written =
                        gocryptfsVolume.writeFile(dstHandleID, offset, ioBuffer, length).toLong()
                    if (written == length.toLong()) {
                        offset += length.toLong()
                    } else {
                        success = false
                        break
                    }
                }
                gocryptfsVolume.closeFile(dstHandleID)
            }
            remoteGocryptfsVolume.closeFile(srcHandleID)
        }
        return success
    }

    private fun safeImportFileFromOtherVolume(remoteGocryptfsVolume: GocryptfsVolume, srcPath: String, dstPath: String): String? {
        val checkedDstPath = checkFileOverwrite(PathUtils.path_join(currentDirectoryPath, File(dstPath).name))
        return if (checkedDstPath == null){
            ""
        } else {
            if (importFileFromOtherVolume(remoteGocryptfsVolume, srcPath, checkedDstPath)) null else dstPath
        }
    }

    private fun recursiveImportDirectoryFromOtherVolume(remote_gocryptfsVolume: GocryptfsVolume, remote_directory_path: String, outputPath: String): String? {
        val mappedElements = gocryptfsVolume.recursiveMapFiles(remote_directory_path)
        val dstDirectoryPath = PathUtils.path_join(outputPath, File(remote_directory_path).name)
        if (!gocryptfsVolume.pathExists(dstDirectoryPath)) {
            if (!gocryptfsVolume.mkdir(dstDirectoryPath)) {
                return dstDirectoryPath
            }
        }
        for (e in mappedElements) {
            val dstPath = PathUtils.path_join(dstDirectoryPath, PathUtils.getRelativePath(remote_directory_path, e.fullPath))
            if (e.isDirectory) {
                if (!gocryptfsVolume.mkdir(dstPath)){
                    return e.fullPath
                }
            } else {
                val checkedDstPath = checkFileOverwrite(dstPath)
                if (checkedDstPath == null){
                    return ""
                } else {
                    if (!importFileFromOtherVolume(remote_gocryptfsVolume, e.fullPath, checkedDstPath)) {
                        return e.fullPath
                    }
                }
            }
        }
        return null
    }

    private fun recursiveExportDirectory(plain_directory_path: String, output_dir: String?): String? {
        if (File(PathUtils.path_join(output_dir, plain_directory_path)).mkdir()) {
            val explorerElements = gocryptfsVolume.listDir(plain_directory_path)
            for (e in explorerElements) {
                val fullPath = PathUtils.path_join(plain_directory_path, e.name)
                if (e.isDirectory) {
                    val failedItem = recursiveExportDirectory(fullPath, output_dir)
                    failedItem?.let { return it }
                } else {
                    if (!gocryptfsVolume.exportFile(fullPath, PathUtils.path_join(output_dir, fullPath))) {
                        return fullPath
                    }
                }
            }
            return null
        }
        return output_dir
    }

    private fun recursiveRemoveDirectory(plain_directory_path: String): String? {
        val explorerElements = gocryptfsVolume.listDir(plain_directory_path)
        for (e in explorerElements) {
            val fullPath = PathUtils.path_join(plain_directory_path, e.name)
            if (e.isDirectory) {
                val result = recursiveRemoveDirectory(fullPath)
                result?.let { return it }
            } else {
                if (!gocryptfsVolume.removeFile(fullPath)) {
                    return fullPath
                }
            }
        }
        return if (!gocryptfsVolume.rmdir(plain_directory_path)) {
            plain_directory_path
        } else {
            null
        }
    }

    private fun removeSelectedItems() {
        var failedItem: String? = null
        for (i in explorerAdapter.selectedItems) {
            val element = explorerAdapter.getItem(i)
            val fullPath = PathUtils.path_join(currentDirectoryPath, element.name)
            if (element.isDirectory) {
                val result = recursiveRemoveDirectory(fullPath)
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
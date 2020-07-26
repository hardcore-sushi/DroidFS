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
import com.github.clans.fab.FloatingActionMenu
import kotlinx.android.synthetic.main.activity_explorer.*
import sushi.hardcore.droidfs.OpenActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.ExternalProvider
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.io.File
import java.util.*

class ExplorerActivity : BaseExplorerActivity() {
    private val PICK_DIRECTORY_REQUEST_CODE = 1
    private val PICK_FILES_REQUEST_CODE = 2
    private val PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE = 3
    private var usf_decrypt = false
    private var usf_share = false
    override fun init() {
        setContentView(R.layout.activity_explorer)
        usf_decrypt = sharedPrefs.getBoolean("usf_decrypt", false)
        usf_share = sharedPrefs.getBoolean("usf_share", false)
    }

    private fun createNewFile(fileName: String){
        if (fileName.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            val handleID = gocryptfsVolume.open_write_mode(PathUtils.path_join(currentDirectoryPath, fileName))
            if (handleID == -1) {
                ColoredAlertDialog(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.file_creation_failed)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                gocryptfsVolume.close_file(handleID)
                setCurrentPath(currentDirectoryPath)
                invalidateOptionsMenu()
            }
        }
    }

    fun onClickCreateFile(view: View) {
        findViewById<FloatingActionMenu>(R.id.fam_explorer).close(true)
        val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
        val dialog = ColoredAlertDialog(this)
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

    fun onClickAddFile(view: View?) {
        fam_explorer.close(true)
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "*/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(i, PICK_FILES_REQUEST_CODE)
    }

    fun onClickAddFileFromOtherVolume(view: View?) {
        fam_explorer.close(true)
        val intent = Intent(this, OpenActivity::class.java)
        intent.action = "pick"
        startActivityForResult(intent, PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE)
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
                if (uris.isNotEmpty()){
                    var success = true
                    for (uri in uris) {
                        val dstPath = PathUtils.path_join(currentDirectoryPath, PathUtils.getFilenameFromURI(this, uri))
                        contentResolver.openInputStream(uri)?.let {
                            success = gocryptfsVolume.import_file(it, dstPath)
                        }
                        if (!success) {
                            ColoredAlertDialog(this)
                                    .setTitle(R.string.error)
                                    .setMessage(getString(R.string.import_failed, uri))
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            break
                        }
                    }
                    if (success) {
                        ColoredAlertDialog(this)
                                .setTitle(R.string.success_import)
                                .setMessage("""
                                    ${getString(R.string.success_import_msg)}
                                    ${getString(R.string.ask_for_wipe)}
                                    """.trimIndent())
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    success = true
                                    for (uri in uris) {
                                        if (!Wiper.wipe(this, uri)) {
                                            ColoredAlertDialog(this)
                                                    .setTitle(R.string.error)
                                                    .setMessage(getString(R.string.wipe_failed, uri))
                                                    .setPositiveButton(R.string.ok, null)
                                                    .show()
                                            success = false
                                            break
                                        }
                                    }
                                    if (success) {
                                        ColoredAlertDialog(this)
                                                .setTitle(R.string.wipe_successful)
                                                .setMessage(R.string.wipe_success_msg)
                                                .setPositiveButton(R.string.ok, null)
                                                .show()
                                    }
                                }
                                .setNegativeButton(getString(R.string.no), null)
                                .show()
                    }
                    setCurrentPath(currentDirectoryPath)
                }
            }
        } else if (requestCode == PICK_DIRECTORY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                val outputDir = PathUtils.getFullPathFromTreeUri(uri, this)
                var failedItem: String? = null
                for (i in explorerAdapter.selectedItems) {
                    val element = explorerAdapter.getItem(i)
                    val fullPath = PathUtils.path_join(currentDirectoryPath, element.name)
                    failedItem = if (element.isDirectory) {
                        recursiveExportDirectory(fullPath, outputDir)
                    } else {
                        if (gocryptfsVolume.export_file(fullPath, PathUtils.path_join(outputDir, element.name))) null else fullPath
                    }
                    if (failedItem != null) {
                        ColoredAlertDialog(this)
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.export_failed, failedItem))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        break
                    }
                }
                if (failedItem == null) {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.success_export)
                            .setMessage(R.string.success_export_msg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
            }
            explorerAdapter.unSelectAll()
            invalidateOptionsMenu()
        } else if (requestCode == PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val remoteSessionID = data.getIntExtra("sessionID", -1)
                val remoteGocryptfsVolume = GocryptfsVolume(remoteSessionID)
                val path = data.getStringExtra("path")
                var failedItem: String? = null
                if (path == null) {
                    val paths = data.getStringArrayListExtra("paths")
                    val types = data.getIntegerArrayListExtra("types")
                    if (types != null && paths != null){
                        for (i in paths.indices) {
                            failedItem = if (types[i] == 0) { //directory
                                recursiveImportDirectoryFromOtherVolume(remoteGocryptfsVolume, paths[i], currentDirectoryPath)
                            } else {
                                if (importFileFromOtherVolume(remoteGocryptfsVolume, paths[i], currentDirectoryPath)) null else paths[i]
                            }
                            if (failedItem != null) {
                                break
                            }
                        }
                    }
                } else {
                    failedItem = if (importFileFromOtherVolume(remoteGocryptfsVolume, path, currentDirectoryPath)) null else path
                }
                if (failedItem == null) {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.success_import)
                            .setMessage(R.string.success_import_msg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                } else {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.error)
                            .setMessage(getString(R.string.import_failed, failedItem))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
                remoteGocryptfsVolume.close()
                setCurrentPath(currentDirectoryPath)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer, menu)
        handleMenuItems(menu)
        if (usf_share){
            menu.findItem(R.id.explorer_menu_share).isVisible = false
        }
        val anyItemSelected = explorerAdapter.selectedItems.isNotEmpty()
        menu.findItem(R.id.explorer_menu_select_all).isVisible = anyItemSelected
        menu.findItem(R.id.explorer_menu_delete).isVisible = anyItemSelected
        menu.findItem(R.id.explorer_menu_decrypt).isVisible = anyItemSelected && usf_decrypt
        if (anyItemSelected && usf_share){
            var containsDir = false
            for (i in explorerAdapter.selectedItems) {
                if (explorerElements[i].isDirectory) {
                    containsDir = true
                    break
                }
            }
            if (!containsDir) {
                menu.findItem(R.id.explorer_menu_share).isVisible = true
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.explorer_menu_select_all -> {
                explorerAdapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.explorer_menu_delete -> {
                val size = explorerAdapter.selectedItems.size
                val dialog = ColoredAlertDialog(this)
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
            R.id.explorer_menu_share -> {
                val paths: MutableList<String> = ArrayList()
                for (i in explorerAdapter.selectedItems) {
                    val e = explorerElements[i]
                    paths.add(PathUtils.path_join(currentDirectoryPath, e.name))
                }
                ExternalProvider.share(this, gocryptfsVolume, paths)
                explorerAdapter.unSelectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.explorer_menu_decrypt -> {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(i, PICK_DIRECTORY_REQUEST_CODE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun importFileFromOtherVolume(remote_gocryptfsVolume: GocryptfsVolume, full_path: String, output_dir: String): Boolean {
        val outputPath = PathUtils.path_join(output_dir, File(full_path).name)
        var success = true
        val srcHandleID = remote_gocryptfsVolume.open_read_mode(full_path)
        if (srcHandleID != -1) {
            val dstHandleID = gocryptfsVolume.open_write_mode(outputPath)
            if (dstHandleID != -1) {
                var length: Int
                val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
                var offset: Long = 0
                while (remote_gocryptfsVolume.read_file(srcHandleID, offset, ioBuffer).also { length = it } > 0){
                    val written = gocryptfsVolume.write_file(dstHandleID, offset, ioBuffer, length).toLong()
                    if (written == length.toLong()) {
                        offset += length.toLong()
                    } else {
                        success = false
                        break
                    }
                }
                gocryptfsVolume.close_file(dstHandleID)
            }
            remote_gocryptfsVolume.close_file(srcHandleID)
        }
        return success
    }

    private fun recursiveImportDirectoryFromOtherVolume(remote_gocryptfsVolume: GocryptfsVolume, remote_directory_path: String, output_dir: String): String? {
        val directoryPath = PathUtils.path_join(output_dir, File(remote_directory_path).name)
        if (!gocryptfsVolume.path_exists(directoryPath)) {
            if (!gocryptfsVolume.mkdir(directoryPath)) {
                return directoryPath
            }
        }
        val explorerElements = remote_gocryptfsVolume.list_dir(remote_directory_path)
        for (e in explorerElements) {
            val fullPath = PathUtils.path_join(remote_directory_path, e.name)
            if (e.isDirectory) {
                val failedItem = recursiveImportDirectoryFromOtherVolume(remote_gocryptfsVolume, fullPath, directoryPath)
                failedItem?.let { return it }
            } else {
                if (!importFileFromOtherVolume(remote_gocryptfsVolume, fullPath, directoryPath)) {
                    return fullPath
                }
            }
        }
        return null
    }

    private fun recursiveExportDirectory(plain_directory_path: String, output_dir: String?): String? {
        if (File(PathUtils.path_join(output_dir, plain_directory_path)).mkdir()) {
            val explorerElements = gocryptfsVolume.list_dir(plain_directory_path)
            for (e in explorerElements) {
                val fullPath = PathUtils.path_join(plain_directory_path, e.name)
                if (e.isDirectory) {
                    val failedItem = recursiveExportDirectory(fullPath, output_dir)
                    failedItem?.let { return it }
                } else {
                    if (!gocryptfsVolume.export_file(fullPath, PathUtils.path_join(output_dir, fullPath))) {
                        return fullPath
                    }
                }
            }
            return null
        }
        return output_dir
    }

    private fun recursiveRemoveDirectory(plain_directory_path: String): String? {
        val explorerElements = gocryptfsVolume.list_dir(plain_directory_path)
        for (e in explorerElements) {
            val fullPath = PathUtils.path_join(plain_directory_path, e.name)
            if (e.isDirectory) {
                val result = recursiveRemoveDirectory(fullPath)
                result?.let { return it }
            } else {
                if (!gocryptfsVolume.remove_file(fullPath)) {
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
                if (!gocryptfsVolume.remove_file(fullPath)) {
                    failedItem = fullPath
                }
            }
            if (failedItem != null) {
                ColoredAlertDialog(this)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.remove_failed, failedItem))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                break
            }
        }
        explorerAdapter.unSelectAll()
        invalidateOptionsMenu()
        setCurrentPath(currentDirectoryPath) //refresh
    }
}
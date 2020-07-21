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
import sushi.hardcore.droidfs.util.FilesUtils
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
            val handleID = gocryptfsVolume.open_write_mode(FilesUtils.path_join(current_path, fileName))
            if (handleID == -1) {
                ColoredAlertDialog(this)
                    .setTitle(R.string.error)
                    .setMessage(getString(R.string.file_creation_failed))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                gocryptfsVolume.close_file(handleID)
                setCurrentPath(current_path)
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
                        val dstPath = FilesUtils.path_join(current_path, FilesUtils.getFilenameFromURI(this, uri))
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
                    setCurrentPath(current_path)
                }
            }
        } else if (requestCode == PICK_DIRECTORY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                val output_dir = FilesUtils.getFullPathFromTreeUri(uri, this)
                var failed_item: String? = null
                for (i in explorer_adapter.selectedItems) {
                    val element = explorer_adapter.getItem(i)
                    val full_path = FilesUtils.path_join(current_path, element.name)
                    failed_item = if (element.isDirectory) {
                        recursive_export_directory(full_path, output_dir)
                    } else {
                        if (gocryptfsVolume.export_file(full_path, FilesUtils.path_join(output_dir, element.name))) null else full_path
                    }
                    if (failed_item != null) {
                        ColoredAlertDialog(this)
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.export_failed, failed_item))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        break
                    }
                }
                if (failed_item == null) {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.success_export)
                            .setMessage(R.string.success_export_msg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
            }
            explorer_adapter.unSelectAll()
            invalidateOptionsMenu()
        } else if (requestCode == PICK_OTHER_VOLUME_ITEMS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val remote_sessionID = data.getIntExtra("sessionID", -1)
                val remote_gocryptfsVolume = GocryptfsVolume(remote_sessionID)
                val path = data.getStringExtra("path")
                var failed_item: String? = null
                if (path == null) {
                    val paths = data.getStringArrayListExtra("paths")
                    val types = data.getIntegerArrayListExtra("types")
                    if (types != null && paths != null){
                        for (i in paths.indices) {
                            failed_item = if (types[i] == 0) { //directory
                                recursive_import_directory_from_other_volume(remote_gocryptfsVolume, paths[i], current_path)
                            } else {
                                if (import_file_from_other_volume(remote_gocryptfsVolume, paths[i], current_path)) null else paths[i]
                            }
                            if (failed_item != null) {
                                break
                            }
                        }
                    }
                } else {
                    failed_item = if (import_file_from_other_volume(remote_gocryptfsVolume, path, current_path)) null else path
                }
                if (failed_item == null) {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.success_import)
                            .setMessage(R.string.success_import_msg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                } else {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.error)
                            .setMessage(getString(R.string.import_failed, failed_item))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
                remote_gocryptfsVolume.close()
                setCurrentPath(current_path)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer, menu)
        handleMenuItems(menu)
        if (usf_share){
            menu.findItem(R.id.explorer_menu_share).isVisible = false
        }
        val any_item_selected = explorer_adapter.selectedItems.isNotEmpty()
        menu.findItem(R.id.explorer_menu_select_all).isVisible = any_item_selected
        menu.findItem(R.id.explorer_menu_delete).isVisible = any_item_selected
        menu.findItem(R.id.explorer_menu_decrypt).isVisible = any_item_selected && usf_decrypt
        if (any_item_selected && usf_share){
            var containsDir = false
            for (i in explorer_adapter.selectedItems) {
                if (explorer_elements[i].isDirectory) {
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
                explorer_adapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.explorer_menu_delete -> {
                val size = explorer_adapter.selectedItems.size
                val dialog = ColoredAlertDialog(this)
                dialog.setTitle(R.string.warning)
                dialog.setPositiveButton(R.string.ok) { _, _ -> remove_selected_items() }
                dialog.setNegativeButton(R.string.cancel, null)
                if (size > 1) {
                    dialog.setMessage(getString(R.string.multiple_delete_confirm, explorer_adapter.selectedItems.size.toString()))
                } else {
                    dialog.setMessage(getString(R.string.single_delete_confirm, explorer_adapter.getItem(explorer_adapter.selectedItems[0]).name))
                }
                dialog.show()
                true
            }
            R.id.explorer_menu_share -> {
                val paths: MutableList<String> = ArrayList()
                for (i in explorer_adapter.selectedItems) {
                    val e = explorer_elements[i]
                    paths.add(FilesUtils.path_join(current_path, e.name))
                }
                ExternalProvider.share(this, gocryptfsVolume, paths)
                explorer_adapter.unSelectAll()
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

    private fun import_file_from_other_volume(remote_gocryptfsVolume: GocryptfsVolume, full_path: String, output_dir: String): Boolean {
        val output_path = FilesUtils.path_join(output_dir, File(full_path).name)
        var success = true
        val src_handleID = remote_gocryptfsVolume.open_read_mode(full_path)
        if (src_handleID != -1) {
            val dst_handleID = gocryptfsVolume.open_write_mode(output_path)
            if (dst_handleID != -1) {
                var length: Int
                val io_buffer = ByteArray(GocryptfsVolume.DefaultBS)
                var offset: Long = 0
                while (remote_gocryptfsVolume.read_file(src_handleID, offset, io_buffer).also { length = it } > 0){
                    val written = gocryptfsVolume.write_file(dst_handleID, offset, io_buffer, length).toLong()
                    if (written == length.toLong()) {
                        offset += length.toLong()
                    } else {
                        success = false
                        break
                    }
                }
                gocryptfsVolume.close_file(dst_handleID)
            }
            remote_gocryptfsVolume.close_file(src_handleID)
        }
        return success
    }

    private fun recursive_import_directory_from_other_volume(remote_gocryptfsVolume: GocryptfsVolume, remote_directory_path: String, output_dir: String): String? {
        val directory_path = FilesUtils.path_join(output_dir, File(remote_directory_path).name)
        if (!gocryptfsVolume.path_exists(directory_path)) {
            if (!gocryptfsVolume.mkdir(directory_path)) {
                return directory_path
            }
        }
        val explorer_elements = remote_gocryptfsVolume.list_dir(remote_directory_path)
        for (e in explorer_elements) {
            val full_path = FilesUtils.path_join(remote_directory_path, e.name)
            if (e.isDirectory) {
                val failed_item = recursive_import_directory_from_other_volume(remote_gocryptfsVolume, full_path, directory_path)
                failed_item?.let { return it }
            } else {
                if (!import_file_from_other_volume(remote_gocryptfsVolume, full_path, directory_path)) {
                    return full_path
                }
            }
        }
        return null
    }

    private fun recursive_export_directory(plain_directory_path: String, output_dir: String?): String? {
        if (File(FilesUtils.path_join(output_dir, plain_directory_path)).mkdir()) {
            val explorer_elements = gocryptfsVolume.list_dir(plain_directory_path)
            for (e in explorer_elements) {
                val full_path = FilesUtils.path_join(plain_directory_path, e.name)
                if (e.isDirectory) {
                    val failed_item = recursive_export_directory(full_path, output_dir)
                    failed_item?.let { return it }
                } else {
                    if (!gocryptfsVolume.export_file(full_path, FilesUtils.path_join(output_dir, full_path))) {
                        return full_path
                    }
                }
            }
            return null
        }
        return output_dir
    }

    private fun recursive_remove_directory(plain_directory_path: String): String? {
        val explorer_elements = gocryptfsVolume.list_dir(plain_directory_path)
        for (e in explorer_elements) {
            val full_path = FilesUtils.path_join(plain_directory_path, e.name)
            if (e.isDirectory) {
                val result = recursive_remove_directory(full_path)
                result?.let { return it }
            } else {
                if (!gocryptfsVolume.remove_file(full_path)) {
                    return full_path
                }
            }
        }
        return if (!gocryptfsVolume.rmdir(plain_directory_path)) {
            plain_directory_path
        } else {
            null
        }
    }

    private fun remove_selected_items() {
        var failed_item: String? = null
        for (i in explorer_adapter.selectedItems) {
            val element = explorer_adapter.getItem(i)
            val full_path = FilesUtils.path_join(current_path, element.name)
            if (element.isDirectory) {
                val result = recursive_remove_directory(full_path)
                result?.let{ failed_item = it }
            } else {
                if (!gocryptfsVolume.remove_file(full_path)) {
                    failed_item = full_path
                }
            }
            if (failed_item != null) {
                ColoredAlertDialog(this)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.remove_failed, failed_item))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                break
            }
        }
        explorer_adapter.unSelectAll()
        invalidateOptionsMenu()
        setCurrentPath(current_path) //refresh
    }
}
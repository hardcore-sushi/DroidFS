package sushi.hardcore.droidfs.explorers

import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.FilesUtils
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog

class ExplorerActivityDrop : ExplorerActivityRO() {
    override fun init() {
        setContentView(R.layout.activity_explorer_drop)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer_drop, menu)
        handleMenuItems(menu)
        menu.findItem(R.id.explorer_menu_validate).isVisible = explorer_adapter.selectedItems.isEmpty()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.explorer_menu_validate -> {
                val alertDialog = ColoredAlertDialog(this)
                alertDialog.setCancelable(false)
                alertDialog.setPositiveButton(R.string.ok) { _, _ -> finish() }
                var error_msg: String? = null
                val extras = intent.extras
                if (extras != null && extras.containsKey(Intent.EXTRA_STREAM)){
                    if (intent.action == Intent.ACTION_SEND) {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        val output_path = FilesUtils.path_join(current_path, FilesUtils.getFilenameFromURI(this, uri))
                        error_msg = if (gocryptfsVolume.import_file(this, uri, output_path)) null else getString(R.string.import_failed, output_path)
                    } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        if (uris != null){
                            for (uri in uris) {
                                val output_path = FilesUtils.path_join(current_path, FilesUtils.getFilenameFromURI(this, uri))
                                if (!gocryptfsVolume.import_file(this, uri, output_path)) {
                                    error_msg = getString(R.string.import_failed, output_path)
                                    break
                                }
                            }
                        } else {
                            error_msg = getString(R.string.share_intent_parsing_failed)
                        }
                    } else {
                        error_msg = getString(R.string.share_intent_parsing_failed)
                    }
                } else {
                    error_msg = getString(R.string.share_intent_parsing_failed)
                }
                if (error_msg == null) {
                    alertDialog.setTitle(R.string.success_import)
                    alertDialog.setMessage(R.string.success_import_msg)
                } else {
                    alertDialog.setTitle(R.string.error)
                    alertDialog.setMessage(error_msg)
                }
                alertDialog.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
package sushi.hardcore.droidfs.explorers

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

class ExplorerActivityDrop : BaseExplorerActivity() {
    override fun init() {
        setContentView(R.layout.activity_explorer_drop)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer_drop, menu)
        handleMenuItems(menu)
        menu.findItem(R.id.validate).isVisible = explorerAdapter.selectedItems.isEmpty()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.validate -> {
                val alertDialog = ColoredAlertDialogBuilder(this)
                alertDialog.setCancelable(false)
                alertDialog.setPositiveButton(R.string.ok) { _, _ -> finish() }
                var errorMsg: String? = null
                val extras = intent.extras
                if (extras != null && extras.containsKey(Intent.EXTRA_STREAM)){
                    if (intent.action == Intent.ACTION_SEND) {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        val outputPath = PathUtils.path_join(currentDirectoryPath, PathUtils.getFilenameFromURI(this, uri))
                        errorMsg = if (gocryptfsVolume.import_file(this, uri, outputPath)) null else getString(R.string.import_failed, outputPath)
                    } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        if (uris != null){
                            for (uri in uris) {
                                val outputPath = PathUtils.path_join(currentDirectoryPath, PathUtils.getFilenameFromURI(this, uri))
                                if (!gocryptfsVolume.import_file(this, uri, outputPath)) {
                                    errorMsg = getString(R.string.import_failed, outputPath)
                                    break
                                }
                            }
                        } else {
                            errorMsg = getString(R.string.share_intent_parsing_failed)
                        }
                    } else {
                        errorMsg = getString(R.string.share_intent_parsing_failed)
                    }
                } else {
                    errorMsg = getString(R.string.share_intent_parsing_failed)
                }
                if (errorMsg == null) {
                    alertDialog.setTitle(R.string.success_import)
                    alertDialog.setMessage(R.string.success_import_msg)
                } else {
                    alertDialog.setTitle(R.string.error)
                    alertDialog.setMessage(errorMsg)
                }
                alertDialog.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
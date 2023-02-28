package sushi.hardcore.droidfs.explorers

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.IntentUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder

class ExplorerActivityDrop : BaseExplorerActivity() {

    override fun init() {
        super.init()
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            openDialogCreateFolder()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer_drop, menu)
        val result = super.onCreateOptionsMenu(menu)
        menu.findItem(R.id.validate).isVisible = explorerAdapter.selectedItems.isEmpty()
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.validate -> {
                val extras = intent.extras
                val errorMsg: String? = if (extras != null && extras.containsKey(Intent.EXTRA_STREAM)) {
                    when (intent.action) {
                        Intent.ACTION_SEND -> {
                            val uri = IntentUtils.getParcelableExtra<Uri>(intent, Intent.EXTRA_STREAM)
                            if (uri == null) {
                                getString(R.string.share_intent_parsing_failed)
                            } else {
                                importFilesFromUris(listOf(uri), ::onImported)
                                null
                            }
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            val uris: List<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            } else {
                                @Suppress("Deprecation")
                                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                            }
                            if (uris != null) {
                                importFilesFromUris(uris, ::onImported)
                                null
                            } else {
                                getString(R.string.share_intent_parsing_failed)
                            }
                        }
                        else -> getString(R.string.share_intent_parsing_failed)
                    }
                } else {
                    getString(R.string.share_intent_parsing_failed)
                }
                errorMsg?.let {
                    CustomAlertDialogBuilder(this, theme)
                            .setTitle(R.string.error)
                            .setMessage(it)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onImported(failedItem: String?){
        setCurrentPath(currentDirectoryPath)
        if (failedItem == null) {
            CustomAlertDialogBuilder(this, theme)
                    .setTitle(R.string.success_import)
                    .setMessage(R.string.success_import_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok){_, _ ->
                        finish()
                    }
                    .show()
        } else {
            CustomAlertDialogBuilder(this, theme)
                    .setTitle(R.string.error)
                    .setMessage(getString(R.string.import_failed, failedItem))
                    .setPositiveButton(R.string.ok, null)
                    .show()
        }
    }
}
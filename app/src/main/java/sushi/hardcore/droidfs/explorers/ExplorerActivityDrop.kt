package sushi.hardcore.droidfs.explorers

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.LoadingTask
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

class ExplorerActivityDrop : BaseExplorerActivity() {
    override fun init() {
        setContentView(R.layout.activity_explorer_drop)
    }

    fun onClickFAB(view: View) {
        openDialogCreateFolder()
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
                object : LoadingTask(this, R.string.loading_msg_import) {
                    override fun doTask(activity: AppCompatActivity) {
                        val alertDialog = ColoredAlertDialogBuilder(activity)
                        alertDialog.setCancelable(false)
                        alertDialog.setPositiveButton(R.string.ok) { _, _ -> finish() }
                        val errorMsg: String?
                        val extras = intent.extras
                        if (extras != null && extras.containsKey(Intent.EXTRA_STREAM)){
                            Looper.prepare()
                            when (intent.action) {
                                Intent.ACTION_SEND -> {
                                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                                    errorMsg = if (uri == null){
                                        getString(R.string.share_intent_parsing_failed)
                                    } else {
                                        if (importFilesFromUris(listOf(uri), this){ _, _ -> finish() }) null else ""
                                    }
                                }
                                Intent.ACTION_SEND_MULTIPLE -> {
                                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                                    errorMsg = if (uris != null){
                                        if (importFilesFromUris(uris, this){ _, _ -> finish() }) null else ""
                                    } else {
                                        getString(R.string.share_intent_parsing_failed)
                                    }
                                }
                                else -> {
                                    errorMsg = getString(R.string.share_intent_parsing_failed)
                                }
                            }
                        } else {
                            errorMsg = getString(R.string.share_intent_parsing_failed)
                        }
                        if (errorMsg == null || errorMsg.isNotEmpty()){
                            if (errorMsg == null) {
                                alertDialog.setTitle(R.string.success_import)
                                alertDialog.setMessage(R.string.success_import_msg)
                            } else if (errorMsg.isNotEmpty()) {
                                alertDialog.setTitle(R.string.error)
                                alertDialog.setMessage(errorMsg)
                            }
                            stopTask { alertDialog.show() }
                        }
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
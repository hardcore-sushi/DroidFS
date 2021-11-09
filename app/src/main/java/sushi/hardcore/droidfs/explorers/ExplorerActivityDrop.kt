package sushi.hardcore.droidfs.explorers

import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityExplorerDropBinding
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder

class ExplorerActivityDrop : BaseExplorerActivity() {
    private lateinit var binding: ActivityExplorerDropBinding

    override fun init() {
        binding = ActivityExplorerDropBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.fab.setOnClickListener {
            openDialogCreateFolder()
        }
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
                val extras = intent.extras
                val errorMsg: String? = if (extras != null && extras.containsKey(Intent.EXTRA_STREAM)) {
                    when (intent.action) {
                        Intent.ACTION_SEND -> {
                            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            if (uri == null) {
                                getString(R.string.share_intent_parsing_failed)
                            } else {
                                importFilesFromUris(listOf(uri), ::onImported)
                                null
                            }
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
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
                    CustomAlertDialogBuilder(this, themeValue)
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
            CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.success_import)
                    .setMessage(R.string.success_import_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok){_, _ ->
                        finish()
                    }
                    .show()
        } else {
            CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.error)
                    .setMessage(getString(R.string.import_failed, failedItem))
                    .setPositiveButton(R.string.ok, null)
                    .show()
        }
    }
}
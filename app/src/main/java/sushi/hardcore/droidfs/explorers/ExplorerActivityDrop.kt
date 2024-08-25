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
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

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
                val success = if (extras != null && extras.containsKey(Intent.EXTRA_STREAM)) {
                    when (intent.action) {
                        Intent.ACTION_SEND -> {
                            val uri = IntentUtils.getParcelableExtra<Uri>(intent, Intent.EXTRA_STREAM)
                            if (uri == null) {
                                false
                            } else {
                                importFilesFromUris(listOf(uri), ::onImported)
                                true
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
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else if ((intent.clipData?.itemCount ?: 0) > 0) {
                    val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(intent.clipData!!.getItemAt(0).text))
                    val byteArray = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(byteArray)
                    val size = byteArray.size.toLong()
                    createNewFile {
                        var offset = 0L
                        while (offset < size) {
                            offset += encryptedVolume.write(it, offset, byteArray, offset, size-offset)
                        }
                        encryptedVolume.closeFile(it)
                        onImported()
                    }
                    true
                } else {
                    false
                }
                if (!success) {
                    CustomAlertDialogBuilder(this, theme)
                            .setTitle(R.string.error)
                            .setMessage(R.string.share_intent_parsing_failed)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onImported() {
        setCurrentPath(currentDirectoryPath)
        CustomAlertDialogBuilder(this, theme)
            .setTitle(R.string.success_import)
            .setMessage(R.string.success_import_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ ->
                finish()
            }
            .show()
    }
}
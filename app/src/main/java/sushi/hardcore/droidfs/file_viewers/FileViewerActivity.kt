package sushi.hardcore.droidfs.file_viewers

import android.os.Bundle
import android.view.View
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

abstract class FileViewerActivity: BaseActivity() {
    lateinit var gocryptfsVolume: GocryptfsVolume
    lateinit var filePath: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = intent.getStringExtra("path")!!
        val sessionID = intent.getIntExtra("sessionID", -1)
        gocryptfsVolume = GocryptfsVolume(sessionID)
        hideSystemUi()
        viewFile()
    }
    open fun hideSystemUi(){
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
            View.SYSTEM_UI_FLAG_FULLSCREEN/* or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION*/
    }
    abstract fun viewFile()
    fun loadWholeFile(path: String): ByteArray? {
        val fileSize = gocryptfsVolume.get_size(path)
        if (fileSize >= 0){
            try {
                val fileBuff = ByteArray(fileSize.toInt())
                var success = false
                val handleID = gocryptfsVolume.open_read_mode(path)
                if (handleID != -1) {
                    var offset: Long = 0
                    val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
                    var length: Int
                    while (gocryptfsVolume.read_file(handleID, offset, ioBuffer).also { length = it } > 0){
                        System.arraycopy(ioBuffer, 0, fileBuff, offset.toInt(), length)
                        offset += length.toLong()
                    }
                    gocryptfsVolume.close_file(handleID)
                    success = offset == fileBuff.size.toLong()
                }
                if (success){
                    return fileBuff
                } else {
                    ColoredAlertDialogBuilder(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.read_file_failed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .show()
                }
            } catch (e: OutOfMemoryError){
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.outofmemoryerror_msg)
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
                    .show()
            }

        } else {
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.get_size_failed)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> finish() }
                .show()
        }
        return null
    }
}
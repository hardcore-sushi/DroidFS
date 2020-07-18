package sushi.hardcore.droidfs.file_viewers

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.ColoredActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.provider.TemporaryFileProvider
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.io.File
import java.util.ArrayList

abstract class FileViewerActivity: ColoredActivity() {
    private var cachedFiles: MutableList<Uri> = ArrayList()
    lateinit var gocryptfsVolume: GocryptfsVolume
    lateinit var filePath: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sharedPrefs.getBoolean("usf_screenshot", false)){
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        filePath = intent.getStringExtra("path")!!
        val sessionID = intent.getIntExtra("sessionID", -1)
        gocryptfsVolume = GocryptfsVolume(sessionID)
        toggleFullscreen()
        viewFile()
    }
    open fun toggleFullscreen(){
        var uiOptions = window.decorView.systemUiVisibility
        //uiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        uiOptions = uiOptions xor View.SYSTEM_UI_FLAG_FULLSCREEN
        uiOptions = uiOptions xor View.SYSTEM_UI_FLAG_IMMERSIVE
        window.decorView.systemUiVisibility = uiOptions
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
                    val io_buffer = ByteArray(GocryptfsVolume.DefaultBS)
                    var length: Int
                    while (gocryptfsVolume.read_file(handleID, offset, io_buffer).also { length = it } > 0){
                        System.arraycopy(io_buffer, 0, fileBuff, offset.toInt(), length)
                        offset += length.toLong()
                    }
                    gocryptfsVolume.close_file(handleID)
                    success = offset == fileBuff.size.toLong()
                }
                if (success){
                    return fileBuff
                } else {
                    ColoredAlertDialog(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.read_file_failed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .show()
                }
            } catch (e: OutOfMemoryError){
                ColoredAlertDialog(this)
                    .setTitle(R.string.error)
                    .setMessage(getString(R.string.outofmemoryerror_msg))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
                    .show()
            }

        } else {
            ColoredAlertDialog(this)
                .setTitle(R.string.error)
                .setMessage(R.string.get_size_failed)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> finish() }
                .show()
        }
        return null
    }
    fun exportFile(path: String): Uri? {
        val tmpFileUri = TemporaryFileProvider.createFile(this, File(path).name)
        cachedFiles.add(tmpFileUri)
        return if (gocryptfsVolume.export_file(this, path, tmpFileUri)) {
            tmpFileUri
        } else {
            ColoredAlertDialog(this)
                .setTitle(R.string.error)
                .setMessage(getString(R.string.export_failed, path))
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> finish() }
                .show()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Thread{
            for (uri in cachedFiles) {
                if (Wiper.wipe(this, uri)){
                    cachedFiles.remove(uri)
                }
            }
        }.start()
    }
}
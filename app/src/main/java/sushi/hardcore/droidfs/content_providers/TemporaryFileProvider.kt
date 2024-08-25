package sushi.hardcore.droidfs.content_providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.BuildConfig
import sushi.hardcore.droidfs.EncryptedFileProvider
import sushi.hardcore.droidfs.VolumeManager
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.util.AndroidUtils
import sushi.hardcore.droidfs.util.Wiper
import java.io.File
import java.util.UUID

class TemporaryFileProvider : ContentProvider() {
    private inner class ProvidedFile(
        val file: EncryptedFileProvider.ExportedFile,
        val size: Long,
        val volumeId: Int
    )

    companion object {
        private const val TAG = "TemporaryFileProvider"
        private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".temporary_provider"
        private val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")

        lateinit var instance: TemporaryFileProvider
            private set
    }

    private val usfSafWriteDelegate = AndroidUtils.LiveBooleanPreference("usf_saf_write", false)
    private val usfSafWrite by usfSafWriteDelegate
    private lateinit var volumeManager: VolumeManager
    lateinit var encryptedFileProvider: EncryptedFileProvider
    private val files = HashMap<Uri, ProvidedFile>()

    override fun onCreate(): Boolean {
        return context?.let {
            volumeManager = (it.applicationContext as VolumeManagerApp).volumeManager
            usfSafWriteDelegate.init(it)
            encryptedFileProvider = EncryptedFileProvider(it)
            instance = this
            val tmpFilesDir = EncryptedFileProvider.getTmpFilesDir(it)
            val success = tmpFilesDir.mkdirs()
            // wipe any additional files not previously deleted
            GlobalScope.launch(Dispatchers.IO) {
                tmpFilesDir.listFiles()?.onEach { f -> Wiper.wipe(f) }
            }
            success
        } ?: false
    }

    fun exportFile(
        exportedFile: EncryptedFileProvider.ExportedFile,
        size: Long,
        volumeId: Int
    ): Uri? {
        if (!encryptedFileProvider.exportFile(exportedFile, volumeManager.getVolume(volumeId)!!)) {
            return null
        }
        return Uri.withAppendedPath(BASE_URI, UUID.randomUUID().toString()).also {
            files[it] = ProvidedFile(exportedFile, size, volumeId)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val file = files[uri] ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), 1).apply {
            addRow(arrayOf(File(file.file.path).name, file.size))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Operation not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("Operation not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return if (files.remove(uri)?.file?.also { it.free() } == null) 0 else 1
    }

    override fun getType(uri: Uri): String = files[uri]?.file?.path?.let {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(it).extension)
    } ?: "application/octet-stream"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        files[uri]?.let { file ->
            val encryptedVolume = volumeManager.getVolume(file.volumeId) ?: run {
                Log.e(TAG, "Volume closed for $uri")
                return null
            }
            val result = encryptedFileProvider.openFile(
                file.file,
                mode,
                encryptedVolume,
                volumeManager.getCoroutineScope(file.volumeId),
                false,
                usfSafWrite,
            )
            when (result.second) {
                EncryptedFileProvider.Error.SUCCESS -> return result.first!!
                EncryptedFileProvider.Error.WRITE_ACCESS_DENIED -> Log.e(
                    TAG,
                    "Unauthorized write access requested from $callingPackage to $uri"
                )

                else -> result.second.log()
            }
        }
        return null
    }

    // this must not be cancelled
    fun wipe() = GlobalScope.launch(Dispatchers.IO) {
        context!!.revokeUriPermission(BASE_URI, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        synchronized(this@TemporaryFileProvider) {
            for (i in files.values) {
                i.file.free()
            }
            files.clear()
        }
    }
}
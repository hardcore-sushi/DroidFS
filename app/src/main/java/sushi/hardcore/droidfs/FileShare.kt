package sushi.hardcore.droidfs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.content_providers.TemporaryFileProvider
import java.io.File

class FileShare(context: Context) {
    companion object {
        private const val CONTENT_TYPE_ANY = "*/*"
        private fun getContentType(filename: String, previousContentType: String?): String {
            if (CONTENT_TYPE_ANY != previousContentType) {
                var contentType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(File(filename).extension)
                if (contentType == null) {
                    contentType = CONTENT_TYPE_ANY
                }
                if (previousContentType == null) {
                    return contentType
                } else if (previousContentType != contentType) {
                    return CONTENT_TYPE_ANY
                }
            }
            return previousContentType
        }
    }

    private val usfSafWrite = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("usf_saf_write", false)

    private fun exportFile(exportedFile: EncryptedFileProvider.ExportedFile, size: Long, volumeId: Int, previousContentType: String? = null): Pair<Uri, String>? {
        val uri = TemporaryFileProvider.instance.exportFile(exportedFile, size, volumeId) ?: return null
        return Pair(uri, getContentType(File(exportedFile.path).name, previousContentType))
    }

    fun share(files: List<Pair<String, Long>>, volumeId: Int): Pair<Intent?, Int?> {
        var contentType: String? = null
        val uris = ArrayList<Uri>(files.size)
        for ((path, size) in files) {
            val exportedFile = TemporaryFileProvider.instance.encryptedFileProvider.createFile(path, size)
                ?: return Pair(null, R.string.export_failed_create)
            val result = exportFile(exportedFile, size, volumeId, contentType)
            contentType = if (result == null) {
                return Pair(null, R.string.export_failed_export)
            } else {
                uris.add(result.first)
                result.second
            }
        }
        return Pair(Intent().apply {
            type = contentType
            if (uris.size == 1) {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }, null)
    }

    fun openWith(exportedFile: EncryptedFileProvider.ExportedFile, size: Long, volumeId: Int): Pair<Intent?, Int?> {
        val result = exportFile(exportedFile, size, volumeId)
        return if (result == null) {
            Pair(null, R.string.export_failed_export)
        } else {
            Pair(Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (usfSafWrite) {
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                setDataAndType(result.first, result.second)
            }, null)
        }
    }
}
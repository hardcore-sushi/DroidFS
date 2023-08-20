package sushi.hardcore.droidfs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import sushi.hardcore.droidfs.content_providers.DiskFileProvider
import sushi.hardcore.droidfs.content_providers.MemoryFileProvider
import sushi.hardcore.droidfs.content_providers.TemporaryFileProvider
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.Version
import java.io.File

class FileShare(private val encryptedVolume: EncryptedVolume, private val context: Context) {

    companion object {
        private const val content_type_all = "*/*"
        fun getContentType(filename: String, previousContentType: String?): String {
            if (content_type_all != previousContentType) {
                var contentType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(File(filename).extension)
                if (contentType == null) {
                    contentType = content_type_all
                }
                if (previousContentType == null) {
                    return contentType
                } else if (previousContentType != contentType) {
                    return content_type_all
                }
            }
            return previousContentType
        }
    }

    private val fileProvider: TemporaryFileProvider<*>

    init {
        var provider: MemoryFileProvider? = null
        System.getProperty("os.version")?.let {
            if (Version(it) >= Version("3.17")) {
                provider = MemoryFileProvider()
            }
        }
        fileProvider = provider ?: DiskFileProvider()
    }

    private fun exportFile(path: String, size: Long, previousContentType: String? = null): Pair<Uri?, String?> {
        val fileName = File(path).name
        val uri = fileProvider.newFile(fileName, size)
        if (uri != null) {
            if (encryptedVolume.exportFile(context, path, uri)) {
                return Pair(uri, getContentType(fileName, previousContentType))
            }
        }
        return Pair(null, null)
    }

    fun share(files: List<Pair<String, Long>>): Pair<Intent?, String?> {
        var contentType: String? = null
        val uris = ArrayList<Uri>(files.size)
        for ((path, size) in files) {
            val result = exportFile(path, size, contentType)
            contentType = if (result.first == null) {
                return Pair(null, path)
            } else {
                uris.add(result.first!!)
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

    fun openWith(path: String, size: Long): Intent? {
        val result = exportFile(path, size)
        return if (result.first != null) {
            Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(result.first, result.second)
            }
        } else {
            null
        }
    }
}
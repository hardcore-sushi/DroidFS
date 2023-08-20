package sushi.hardcore.droidfs.content_providers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

abstract class TemporaryFileProvider<T>: ContentProvider() {
    protected inner class SharedFile(val name: String, val size: Long, val file: T)

    protected abstract fun getFile(uri: Uri): SharedFile?
    abstract fun newFile(name: String, size: Long): Uri?

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val file = getFile(uri) ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), 1).apply {
            addRow(arrayOf(file.name, file.size))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Operation not supported")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Operation not supported")
    }

    override fun getType(uri: Uri): String = getFile(uri)?.name?.let {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(it).extension)
    } ?: "application/octet-stream"
}
package sushi.hardcore.droidfs.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import sushi.hardcore.droidfs.BuildConfig
import sushi.hardcore.droidfs.util.Wiper
import java.io.File
import java.util.*
import java.util.regex.Pattern

class RestrictedFileProvider: ContentProvider() {
    companion object {
        private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".temporary_provider"
        private val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        const val TEMPORARY_FILES_DIR_NAME = "temp"
        private val UUID_PATTERN = Pattern.compile("[a-fA-F0-9-]+")

        private lateinit var tempFilesDir: File
        private val tempFiles = mutableMapOf<String, TemporaryFile>()

        class TemporaryFile(val fileName: String, val file: File)

        fun newFile(fileName: String): Uri? {
            val uuid = UUID.randomUUID().toString()
            val file = File(tempFilesDir, uuid)
            return if (file.createNewFile()){
                tempFiles[uuid] = TemporaryFile(fileName, file)
                Uri.withAppendedPath(CONTENT_URI, uuid)
            } else {
                null
            }
        }

        fun wipeAll() {
            tempFilesDir.listFiles()?.let{
                for (file in it) {
                    Wiper.wipe(file)
                }
            }
        }

        private fun getFileFromUri(uri: Uri): TemporaryFile? {
            val uuid = uri.lastPathSegment
            if (uuid != null) {
                if (UUID_PATTERN.matcher(uuid).matches()) {
                    return tempFiles[uuid]
                }
            }
            return null
        }
    }

    override fun onCreate(): Boolean {
        context?.let {
            tempFilesDir = File(it.cacheDir, TEMPORARY_FILES_DIR_NAME)
            return tempFilesDir.mkdirs()
        }
        return false
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw RuntimeException("Operation not supported")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        throw RuntimeException("Operation not supported")
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val temporaryFile = getFileFromUri(uri)
        if (temporaryFile != null) {
            val cursor = MatrixCursor(
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE
                )
            )
            cursor.newRow()
                .add(temporaryFile.fileName)
                .add(temporaryFile.file.length())
            return cursor
        }
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val temporaryFile = getFileFromUri(uri)
        if (temporaryFile != null) {
            Wiper.wipe(temporaryFile.file)
            tempFiles.remove(uri.lastPathSegment)
        }
        return 1
    }

    override fun getType(uri: Uri): String {
        return "application/octet-stream"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (("w" in mode && callingPackage == BuildConfig.APPLICATION_ID) || "w" !in mode) {
            getFileFromUri(uri)?.let{
                return ParcelFileDescriptor.open(it.file, ParcelFileDescriptor.parseMode(mode))
            }
        } else {
            throw SecurityException("Read-only access")
        }
        return null
    }
}
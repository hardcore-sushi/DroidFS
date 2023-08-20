package sushi.hardcore.droidfs.content_providers

import android.net.Uri
import android.os.ParcelFileDescriptor
import sushi.hardcore.droidfs.BuildConfig
import sushi.hardcore.droidfs.util.Wiper
import java.io.File
import java.util.UUID

class DiskFileProvider: TemporaryFileProvider<File>() {
    companion object {
        private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".disk_provider"
        private val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        const val TEMPORARY_FILES_DIR_NAME = "temp"

        private lateinit var tempFilesDir: File

        private var files = HashMap<Uri, TemporaryFileProvider<File>.SharedFile>()

        fun wipe() {
            for (i in files.values) {
                Wiper.wipe(i.file)
            }
            files.clear()
            tempFilesDir.listFiles()?.let {
                for (file in it) {
                    Wiper.wipe(file)
                }
            }
        }
    }

    override fun onCreate(): Boolean {
        context?.let {
            tempFilesDir = File(it.cacheDir, TEMPORARY_FILES_DIR_NAME)
            return tempFilesDir.mkdirs()
        }
        return false
    }

    override fun getFile(uri: Uri): SharedFile? = files[uri]

    override fun newFile(name: String, size: Long): Uri? {
        val uuid = UUID.randomUUID().toString()
        val file = File(tempFilesDir, uuid)
        return if (file.createNewFile()) {
            Uri.withAppendedPath(CONTENT_URI, uuid).also {
                files[it] = SharedFile(name, size, file)
            }
        } else {
            null
        }
    }

    override fun delete(uri: Uri, givenSelection: String?, givenSelectionArgs: Array<String>?): Int {
        return if (files.remove(uri)?.file?.also { Wiper.wipe(it) } == null) 0 else 1
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return if (("w" in mode && callingPackage == BuildConfig.APPLICATION_ID) || "w" !in mode) {
            files[uri]?.file?.let {
                return ParcelFileDescriptor.open(it, ParcelFileDescriptor.parseMode(mode))
            }
        } else {
            throw SecurityException("Read-only access")
        }
    }
}
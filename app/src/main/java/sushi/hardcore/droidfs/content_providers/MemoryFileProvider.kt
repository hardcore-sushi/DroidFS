package sushi.hardcore.droidfs.content_providers

import android.net.Uri
import android.os.ParcelFileDescriptor
import sushi.hardcore.droidfs.BuildConfig
import sushi.hardcore.droidfs.MemFile
import java.io.FileInputStream
import java.util.UUID

class MemoryFileProvider: TemporaryFileProvider<MemFile>() {
    companion object {
        private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".memory_provider"
        private val BASE_URI: Uri = Uri.parse("content://$AUTHORITY")

        private var files = HashMap<Uri, TemporaryFileProvider<MemFile>.SharedFile>()

        fun wipe() {
            for (i in files.values) {
                i.file.close()
            }
            files.clear()
        }
    }

    override fun onCreate(): Boolean = true

    override fun getFile(uri: Uri): SharedFile? = files[uri]

    override fun newFile(name: String, size: Long): Uri? {
        val uuid = UUID.randomUUID().toString()
        return Uri.withAppendedPath(BASE_URI, uuid).also {
            files[it] = SharedFile(name, size, MemFile.create(uuid, size) ?: return null)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return if (files.remove(uri)?.file?.close() == null) 0 else 1
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return files[uri]?.file?.getParcelFileDescriptor()?.also {
            FileInputStream(it.fileDescriptor).apply {
                channel.position(0)
                close()
            }
        }
    }
}
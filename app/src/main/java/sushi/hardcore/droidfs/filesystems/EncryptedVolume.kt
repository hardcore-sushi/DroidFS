package sushi.hardcore.droidfs.filesystems

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import sushi.hardcore.droidfs.GocryptfsVolume
import sushi.hardcore.droidfs.SavedVolume
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

abstract class EncryptedVolume: Parcelable {
    companion object {
        const val GOCRYPTFS_VOLUME_TYPE: Byte = 0
        const val CRYFS_VOLUME_TYPE: Byte = 1

        @JvmField
        val CREATOR = object : Parcelable.Creator<EncryptedVolume> {
            override fun createFromParcel(parcel: Parcel): EncryptedVolume {
                return when (parcel.readByte()) {
                    GOCRYPTFS_VOLUME_TYPE -> GocryptfsVolume(parcel)
                    CRYFS_VOLUME_TYPE -> CryfsVolume(parcel)
                    else -> throw invalidVolumeType()
                }
            }
            override fun newArray(size: Int) = arrayOfNulls<EncryptedVolume>(size)
        }

        fun getVolumeType(path: String): Byte {
            return if (File(path, GocryptfsVolume.CONFIG_FILE_NAME).isFile) {
                GOCRYPTFS_VOLUME_TYPE
            } else if (File(path, CryfsVolume.CONFIG_FILE_NAME).isFile) {
                CRYFS_VOLUME_TYPE
            } else {
                -1
            }
        }

        fun init(volume: SavedVolume, filesDir: String, password: ByteArray?, givenHash: ByteArray?, returnedHash: ByteArray?): EncryptedVolume? {
            return when (volume.type) {
                GOCRYPTFS_VOLUME_TYPE -> {
                    GocryptfsVolume.init(volume.getFullPath(filesDir), password, givenHash, returnedHash)
                }
                CRYFS_VOLUME_TYPE -> {
                    CryfsVolume.init(volume.getFullPath(filesDir), PathUtils.pathJoin(filesDir, "localState"), password!!)
                }
                else -> throw invalidVolumeType()
            }
        }

        private fun invalidVolumeType(): java.lang.RuntimeException {
            return RuntimeException("Invalid volume type")
        }
    }

    override fun describeContents() = 0

    abstract fun openFile(path: String): Long
    abstract fun read(fileHandle: Long, buffer: ByteArray, offset: Long): Int
    abstract fun write(fileHandle: Long, offset: Long, buffer: ByteArray, size: Int): Int
    abstract fun closeFile(fileHandle: Long): Boolean
    abstract fun truncate(path: String, size: Long): Boolean
    abstract fun deleteFile(path: String): Boolean
    abstract fun readDir(path: String): MutableList<ExplorerElement>?
    abstract fun mkdir(path: String): Boolean
    abstract fun rmdir(path: String): Boolean
    abstract fun getAttr(path: String): Stat?
    abstract fun rename(srcPath: String, dstPath: String): Boolean
    abstract fun close()
    abstract fun isClosed(): Boolean

    fun pathExists(path: String): Boolean {
        return getAttr(path) != null
    }

    fun exportFile(fileHandle: Long, os: OutputStream): Boolean {
        var offset: Long = 0
        val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
        var length: Int
        while (read(fileHandle, ioBuffer, offset).also { length = it } > 0){
            os.write(ioBuffer, 0, length)
            offset += length.toLong()
        }
        os.close()
        return true
    }

    fun exportFile(src_path: String, os: OutputStream): Boolean {
        var success = false
        val srcfileHandle = openFile(src_path)
        if (srcfileHandle != -1L) {
            success = exportFile(srcfileHandle, os)
            closeFile(srcfileHandle)
        }
        return success
    }

    fun exportFile(src_path: String, dst_path: String): Boolean {
        return exportFile(src_path, FileOutputStream(dst_path))
    }

    fun exportFile(context: Context, src_path: String, output_path: Uri): Boolean {
        val os = context.contentResolver.openOutputStream(output_path)
        if (os != null){
            return exportFile(src_path, os)
        }
        return false
    }

    fun importFile(inputStream: InputStream, dst_path: String): Boolean {
        val dstfileHandle = openFile(dst_path)
        if (dstfileHandle != -1L) {
            var success = true
            var offset: Long = 0
            val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
            var length: Int
            while (inputStream.read(ioBuffer).also { length = it } > 0) {
                val written = write(dstfileHandle, offset, ioBuffer, length).toLong()
                if (written == length.toLong()) {
                    offset += written
                } else {
                    inputStream.close()
                    success = false
                    break
                }
            }
            closeFile(dstfileHandle)
            inputStream.close()
            return success
        }
        return false
    }

    fun importFile(context: Context, src_uri: Uri, dst_path: String): Boolean {
        val inputStream = context.contentResolver.openInputStream(src_uri)
        if (inputStream != null){
            return importFile(inputStream, dst_path)
        }
        return false
    }

    fun loadWholeFile(fullPath: String, size: Long? = null, maxSize: Long? = null): Pair<ByteArray?, Int> {
        val fileSize = size ?: getAttr(fullPath)?.size ?: -1
        return if (fileSize >= 0) {
            maxSize?.let {
                if (fileSize > it) {
                    return Pair(null, 0)
                }
            }
            try {
                val fileBuff = ByteArray(fileSize.toInt())
                val fileHandle = openFile(fullPath)
                if (fileHandle == -1L) {
                    Pair(null, 3)
                } else {
                    var offset: Long = 0
                    val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
                    var length: Int
                    while (read(fileHandle, ioBuffer, offset).also { length = it } > 0) {
                        System.arraycopy(ioBuffer, 0, fileBuff, offset.toInt(), length)
                        offset += length.toLong()
                    }
                    closeFile(fileHandle)
                    if (offset == fileBuff.size.toLong()) {
                        Pair(fileBuff, 0)
                    } else {
                        Pair(null, 4)
                    }
                }
            } catch (e: OutOfMemoryError) {
                Pair(null, 2)
            }
        } else {
            Pair(null, 1)
        }
    }

    fun recursiveMapFiles(rootPath: String): MutableList<ExplorerElement>? {
        val result = mutableListOf<ExplorerElement>()
        val explorerElements = readDir(rootPath) ?: return null
        result.addAll(explorerElements)
        for (e in explorerElements) {
            if (e.isDirectory) {
                result.addAll(recursiveMapFiles(e.fullPath) ?: return null)
            }
        }
        return result
    }

    fun recursiveRemoveDirectory(path: String): String? {
        readDir(path)?.let { elements ->
            for (e in elements) {
                val fullPath = PathUtils.pathJoin(path, e.name)
                if (e.isDirectory) {
                    val result = recursiveRemoveDirectory(fullPath)
                    result?.let { return it }
                } else {
                    if (!deleteFile(fullPath)) {
                        return fullPath
                    }
                }
            }
        }
        return if (!rmdir(path)) {
            path
        } else {
            null
        }
    }
}
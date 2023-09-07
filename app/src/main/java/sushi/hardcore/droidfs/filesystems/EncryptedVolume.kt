package sushi.hardcore.droidfs.filesystems

import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.ObjRef
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

abstract class EncryptedVolume: Parcelable {

    class InitResult(
        val errorCode: Int,
        val errorStringId: Int,
        val worthRetry: Boolean,
        val volume: EncryptedVolume?,
    ) {
        class Builder {
            var errorCode = 0
            var errorStringId = 0
            var worthRetry = false
            var volume: EncryptedVolume? = null

            fun build() = InitResult(errorCode, errorStringId, worthRetry, volume)
        }
    }

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

        /**
         * Get the type of a volume.
         *
         * @return The volume type or -1 if the path is not recognized as a volume
         */
        fun getVolumeType(path: String): Byte {
            return if (File(path, GocryptfsVolume.CONFIG_FILE_NAME).isFile) {
                GOCRYPTFS_VOLUME_TYPE
            } else if (File(path, CryfsVolume.CONFIG_FILE_NAME).isFile) {
                CRYFS_VOLUME_TYPE
            } else {
                -1
            }
        }

        fun init(
            volume: VolumeData,
            filesDir: String,
            password: ByteArray?,
            givenHash: ByteArray?,
            returnedHash: ObjRef<ByteArray?>?
        ): InitResult {
            return when (volume.type) {
                GOCRYPTFS_VOLUME_TYPE -> {
                    GocryptfsVolume.init(
                        volume.getFullPath(filesDir),
                        password,
                        givenHash,
                        returnedHash?.apply {
                            value = ByteArray(GocryptfsVolume.KeyLen)
                        }?.value
                    )
                }
                CRYFS_VOLUME_TYPE -> {
                    CryfsVolume.init(volume.getFullPath(filesDir), CryfsVolume.getLocalStateDir(filesDir), password, givenHash, returnedHash)
                }
                else -> throw invalidVolumeType()
            }
        }

        private fun invalidVolumeType(): java.lang.RuntimeException {
            return RuntimeException("Invalid volume type")
        }
    }

    override fun describeContents() = 0

    abstract fun openFileReadMode(path: String): Long
    abstract fun openFileWriteMode(path: String): Long
    abstract fun read(fileHandle: Long, fileOffset: Long, buffer: ByteArray, dstOffset: Long, length: Long): Int
    abstract fun write(fileHandle: Long, fileOffset: Long, buffer: ByteArray, srcOffset: Long, length: Long): Int
    abstract fun closeFile(fileHandle: Long): Boolean
    // Due to gocryptfs internals, truncate requires the file to be open before it is called
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
        val ioBuffer = ByteArray(Constants.IO_BUFF_SIZE)
        var length: Int
        while (read(fileHandle, offset, ioBuffer, 0, ioBuffer.size.toLong()).also { length = it } > 0) {
            os.write(ioBuffer, 0, length)
            offset += length.toLong()
        }
        os.close()
        return true
    }

    fun exportFile(src_path: String, os: OutputStream): Boolean {
        var success = false
        val srcfileHandle = openFileReadMode(src_path)
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
        if (os != null) {
            return exportFile(src_path, os)
        }
        return false
    }

    fun importFile(inputStream: InputStream, dst_path: String): Boolean {
        val dstfileHandle = openFileWriteMode(dst_path)
        if (dstfileHandle != -1L) {
            var success = true
            var offset: Long = 0
            val ioBuffer = ByteArray(Constants.IO_BUFF_SIZE)
            var length: Long
            while (inputStream.read(ioBuffer).also { length = it.toLong() } > 0) {
                val written = write(dstfileHandle, offset, ioBuffer, 0, length).toLong()
                if (written == length) {
                    offset += written
                } else {
                    success = false
                    break
                }
            }
            truncate(dst_path, offset)
            closeFile(dstfileHandle)
            inputStream.close()
            return success
        }
        return false
    }

    fun importFile(context: Context, src_uri: Uri, dst_path: String): Boolean {
        val inputStream = context.contentResolver.openInputStream(src_uri)
        if (inputStream != null) {
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
                val fileHandle = openFileReadMode(fullPath)
                if (fileHandle == -1L) {
                    Pair(null, 3)
                } else {
                    var offset: Long = 0
                    while (offset < fileSize && read(fileHandle, offset, fileBuff, offset, fileSize-offset).also { offset += it } > 0) {}
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
}
package sushi.hardcore.droidfs.filesystems

import android.os.Parcel
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.ObjRef
import sushi.hardcore.droidfs.util.PathUtils

class CryfsVolume(private val fusePtr: Long): EncryptedVolume() {
    companion object {
        init {
            System.loadLibrary("cryfs_jni")
        }

        const val CONFIG_FILE_NAME = "cryfs.config"

        private external fun nativeInit(
            baseDir: String,
            localStateDir: String,
            password: ByteArray?,
            givenHash: ByteArray?,
            returnedHash: ObjRef<ByteArray?>?,
            createBaseDir: Boolean,
            cipher: String?,
            errorCode: ObjRef<Int?>,
        ): Long
        private external fun nativeChangeEncryptionKey(
            baseDir: String,
            localStateDir: String,
            currentPassword: ByteArray?,
            givenHash: ByteArray?,
            newPassword: ByteArray,
            returnedHash: ObjRef<ByteArray?>?
        ): Boolean
        private external fun nativeCreate(fusePtr: Long, path: String, mode: Int): Long
        private external fun nativeOpen(fusePtr: Long, path: String, flags: Int): Long
        private external fun nativeRead(fusePtr: Long, fileHandle: Long, fileOffset: Long, buffer: ByteArray, dstOffset: Long, length: Long): Int
        private external fun nativeWrite(fusePtr: Long, fileHandle: Long, fileOffset: Long, buffer: ByteArray, srcOffset: Long, length: Long): Int
        private external fun nativeTruncate(fusePtr: Long, path: String, size: Long): Boolean
        private external fun nativeDeleteFile(fusePtr: Long, path: String): Boolean
        private external fun nativeCloseFile(fusePtr: Long, fileHandle: Long): Boolean
        private external fun nativeReadDir(fusePtr: Long, path: String): MutableList<ExplorerElement>?
        private external fun nativeMkdir(fusePtr: Long, path: String, mode: Int): Boolean
        private external fun nativeRmdir(fusePtr: Long, path: String): Boolean
        private external fun nativeGetAttr(fusePtr: Long, path: String): Stat?
        private external fun nativeRename(fusePtr: Long, srcPath: String, dstPath: String): Boolean
        private external fun nativeClose(fusePtr: Long)
        private external fun nativeIsClosed(fusePtr: Long): Boolean

        fun getLocalStateDir(filesDir: String): String {
            return PathUtils.pathJoin(filesDir, Constants.CRYFS_LOCAL_STATE_DIR)
        }

        private fun init(
            baseDir: String,
            localStateDir: String,
            password: ByteArray?,
            givenHash: ByteArray?,
            returnedHash: ObjRef<ByteArray?>?,
            createBaseDir: Boolean,
            cipher: String?
        ): InitResult {
            val errorCode = ObjRef<Int?>(null)
            val fusePtr = nativeInit(baseDir, localStateDir, password, givenHash, returnedHash, createBaseDir, cipher, errorCode)
            val result = InitResult.Builder()
            if (fusePtr == 0L) {
                result.errorCode = errorCode.value ?: 0
                result.errorStringId = when (errorCode.value) {
                    // Values from src/cryfs/impl/ErrorCodes.h
                    11 -> R.string.wrong_password
                    16 -> R.string.inaccessible_base_dir
                    19 -> R.string.config_load_error
                    20 -> R.string.filesystem_id_changed
                    else -> 0
                }
            } else {
                result.volume = CryfsVolume(fusePtr)
            }
            return result.build()
        }

        fun create(baseDir: String, localStateDir: String, password: ByteArray, returnedHash: ObjRef<ByteArray?>?, cipher: String?): EncryptedVolume? {
            return init(baseDir, localStateDir, password, null, returnedHash, true, cipher).volume
        }

        fun init(baseDir: String, localStateDir: String, password: ByteArray?, givenHash: ByteArray?, returnedHash: ObjRef<ByteArray?>?): InitResult {
            return init(baseDir, localStateDir, password, givenHash, returnedHash, false, null)
        }

        fun changePassword(
            baseDir: String, filesDir: String, currentPassword: ByteArray?,
            givenHash: ByteArray?,
            newPassword: ByteArray,
            returnedHash: ObjRef<ByteArray?>?
        ): Boolean {
            return nativeChangeEncryptionKey(baseDir, getLocalStateDir(filesDir), currentPassword, givenHash, newPassword, returnedHash)
        }
    }

    constructor(parcel: Parcel) : this(parcel.readLong())

    override fun writeToParcel(parcel: Parcel, flags: Int) = with(parcel) {
        writeByte(CRYFS_VOLUME_TYPE)
        writeLong(fusePtr)
    }

    override fun openFileReadMode(path: String): Long {
        return nativeOpen(fusePtr, path, 0)
    }

    override fun openFileWriteMode(path: String): Long {
        val fileHandle = nativeOpen(fusePtr, path, 0)
        return if (fileHandle == -1L) {
            nativeCreate(fusePtr, path, 0)
        } else {
            fileHandle
        }
    }

    override fun read(fileHandle: Long, fileOffset: Long, buffer: ByteArray, dstOffset: Long, length: Long): Int {
        return nativeRead(fusePtr, fileHandle, fileOffset, buffer, dstOffset, length)
    }

    override fun write(fileHandle: Long, fileOffset: Long, buffer: ByteArray, srcOffset: Long, length: Long): Int {
        return nativeWrite(fusePtr, fileHandle, fileOffset, buffer, srcOffset, length)
    }

    override fun truncate(path: String, size: Long): Boolean {
        return nativeTruncate(fusePtr, path, size)
    }

    override fun closeFile(fileHandle: Long): Boolean {
        return nativeCloseFile(fusePtr, fileHandle)
    }

    override fun deleteFile(path: String): Boolean {
        return nativeDeleteFile(fusePtr, path)
    }

    override fun readDir(path: String): MutableList<ExplorerElement>? {
        return nativeReadDir(fusePtr, path)
    }

    override fun mkdir(path: String): Boolean {
        return nativeMkdir(fusePtr, path, Stat.S_IFDIR)
    }

    override fun rmdir(path: String): Boolean {
        return nativeRmdir(fusePtr, path)
    }

    override fun getAttr(path: String): Stat? {
        return nativeGetAttr(fusePtr, path)
    }

    override fun rename(srcPath: String, dstPath: String): Boolean {
        return nativeRename(fusePtr, srcPath, dstPath)
    }

    override fun close() {
        return nativeClose(fusePtr)
    }

    override fun isClosed(): Boolean {
        return nativeIsClosed(fusePtr)
    }
}
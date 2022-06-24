package sushi.hardcore.droidfs.filesystems

import android.os.Parcel
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.explorers.ExplorerElement
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
            password: ByteArray,
            createBaseDir: Boolean,
            cipher: String?
        ): Long
        private external fun nativeCreate(fusePtr: Long, path: String, mode: Int): Long
        private external fun nativeOpen(fusePtr: Long, path: String, flags: Int): Long
        private external fun nativeRead(fusePtr: Long, fileHandle: Long, buffer: ByteArray, offset: Long): Int
        private external fun nativeWrite(fusePtr: Long, fileHandle: Long, offset: Long, buffer: ByteArray, size: Int): Int
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
            return PathUtils.pathJoin(filesDir, ConstValues.CRYFS_LOCAL_STATE_DIR)
        }

        private fun init(baseDir: String, localStateDir: String, password: ByteArray, createBaseDir: Boolean, cipher: String?): CryfsVolume? {
            val fusePtr = nativeInit(baseDir, localStateDir, password, createBaseDir, cipher)
            return if (fusePtr == 0L) {
                null
            } else {
                CryfsVolume(fusePtr)
            }
        }

        fun create(baseDir: String, localStateDir: String, password: ByteArray, cipher: String?): Boolean {
            return init(baseDir, localStateDir, password, true, cipher)?.also { it.close() } != null
        }

        fun init(baseDir: String, localStateDir: String, password: ByteArray): CryfsVolume? {
            return init(baseDir, localStateDir, password, false, null)
        }
    }

    constructor(parcel: Parcel) : this(parcel.readLong())

    override fun writeToParcel(parcel: Parcel, flags: Int) = with(parcel) {
        writeByte(CRYFS_VOLUME_TYPE)
        writeLong(fusePtr)
    }

    override fun openFile(path: String): Long {
        val fileHandle = nativeOpen(fusePtr, path, 0)
        return if (fileHandle == -1L) {
            nativeCreate(fusePtr, path, 0)
        } else {
            fileHandle
        }
    }

    override fun read(fileHandle: Long, buffer: ByteArray, offset: Long): Int {
        return nativeRead(fusePtr, fileHandle, buffer, offset)
    }

    override fun write(fileHandle: Long, offset: Long, buffer: ByteArray, size: Int): Int {
        return nativeWrite(fusePtr, fileHandle, offset, buffer, size)
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
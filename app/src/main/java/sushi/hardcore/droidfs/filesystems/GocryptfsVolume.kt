package sushi.hardcore.droidfs.filesystems

import android.os.Parcel
import sushi.hardcore.droidfs.explorers.ExplorerElement
import kotlin.math.min

class GocryptfsVolume(private val sessionID: Int): EncryptedVolume() {
    private external fun native_close(sessionID: Int)
    private external fun native_is_closed(sessionID: Int): Boolean
    private external fun native_list_dir(sessionID: Int, dir_path: String): MutableList<ExplorerElement>?
    private external fun native_open_read_mode(sessionID: Int, file_path: String): Int
    private external fun native_open_write_mode(sessionID: Int, file_path: String, mode: Int): Int
    private external fun native_read_file(sessionID: Int, handleID: Int, fileOffset: Long, buff: ByteArray, dstOffset: Long, length: Int): Int
    private external fun native_write_file(sessionID: Int, handleID: Int, fileOffset: Long, buff: ByteArray, srcOffset: Long, length: Int): Int
    private external fun native_truncate(sessionID: Int, path: String, offset: Long): Boolean
    private external fun native_close_file(sessionID: Int, handleID: Int)
    private external fun native_remove_file(sessionID: Int, file_path: String): Boolean
    private external fun native_mkdir(sessionID: Int, dir_path: String, mode: Int): Boolean
    private external fun native_rmdir(sessionID: Int, dir_path: String): Boolean
    private external fun native_get_attr(sessionID: Int, file_path: String): Stat?
    private external fun native_rename(sessionID: Int, old_path: String, new_path: String): Boolean

    companion object {
        const val KeyLen = 32
        const val ScryptDefaultLogN = 16
        const val MAX_KERNEL_WRITE = 128*1024
        const val CONFIG_FILE_NAME = "gocryptfs.conf"
        external fun createVolume(root_cipher_dir: String, password: ByteArray, plainTextNames: Boolean, xchacha: Int, logN: Int, creator: String, returnedHash: ByteArray?): Boolean
        private external fun nativeInit(root_cipher_dir: String, password: ByteArray?, givenHash: ByteArray?, returnedHash: ByteArray?): Int
        external fun changePassword(
            root_cipher_dir: String,
            currentPassword: ByteArray?,
            givenHash: ByteArray?,
            newPassword: ByteArray,
            returnedHash: ByteArray?
        ): Boolean

        fun init(root_cipher_dir: String, password: ByteArray?, givenHash: ByteArray?, returnedHash: ByteArray?): GocryptfsVolume? {
            val sessionId = nativeInit(root_cipher_dir, password, givenHash, returnedHash)
            return if (sessionId == -1) {
                null
            } else {
                GocryptfsVolume(sessionId)
            }
        }

        init {
            System.loadLibrary("gocryptfs_jni")
        }
    }

    constructor(parcel: Parcel) : this(parcel.readInt())

    override fun openFile(path: String): Long {
        return native_open_write_mode(sessionID, path, 0).toLong()
    }

    override fun read(fileHandle: Long, fileOffset: Long, buffer: ByteArray, dstOffset: Long, length: Long): Int {
        return native_read_file(sessionID, fileHandle.toInt(), fileOffset, buffer, dstOffset, min(length.toInt(), MAX_KERNEL_WRITE))
    }

    override fun readDir(path: String): MutableList<ExplorerElement>? {
        return native_list_dir(sessionID, path)
    }

    override fun getAttr(path: String): Stat? {
        return native_get_attr(sessionID, path)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) = with(parcel) {
        writeByte(GOCRYPTFS_VOLUME_TYPE)
        writeInt(sessionID)
    }

    override fun close() {
        native_close(sessionID)
    }

    override fun isClosed(): Boolean {
        return native_is_closed(sessionID)
    }

    override fun mkdir(path: String): Boolean {
        return native_mkdir(sessionID, path, 0)
    }

    override fun rmdir(path: String): Boolean {
        return native_rmdir(sessionID, path)
    }

    override fun closeFile(fileHandle: Long): Boolean {
        native_close_file(sessionID, fileHandle.toInt())
        return true
    }

    override fun write(fileHandle: Long, fileOffset: Long, buffer: ByteArray, srcOffset: Long, length: Long): Int {
        return native_write_file(sessionID, fileHandle.toInt(), fileOffset, buffer, srcOffset, min(length.toInt(), MAX_KERNEL_WRITE))
    }

    override fun truncate(path: String, size: Long): Boolean {
        return native_truncate(sessionID, path, size)
    }

    override fun deleteFile(path: String): Boolean {
        return native_remove_file(sessionID, path)
    }

    override fun rename(srcPath: String, dstPath: String): Boolean {
        return native_rename(sessionID, srcPath, dstPath)
    }
}
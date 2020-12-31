package sushi.hardcore.droidfs

import android.content.Context
import android.net.Uri
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class GocryptfsVolume(var sessionID: Int) {
    private external fun native_close(sessionID: Int)
    private external fun native_is_closed(sessionID: Int): Boolean
    private external fun native_list_dir(sessionID: Int, dir_path: String): MutableList<ExplorerElement>
    private external fun native_open_read_mode(sessionID: Int, file_path: String): Int
    private external fun native_open_write_mode(sessionID: Int, file_path: String): Int
    private external fun native_read_file(sessionID: Int, handleID: Int, offset: Long, buff: ByteArray): Int
    private external fun native_write_file(sessionID: Int, handleID: Int, offset: Long, buff: ByteArray, buff_size: Int): Int
    private external fun native_truncate(sessionID: Int, file_path: String, offset: Long): Boolean
    private external fun native_path_exists(sessionID: Int, file_path: String): Boolean
    private external fun native_get_size(sessionID: Int, file_path: String): Long
    private external fun native_close_file(sessionID: Int, handleID: Int)
    private external fun native_remove_file(sessionID: Int, file_path: String): Boolean
    private external fun native_mkdir(sessionID: Int, dir_path: String): Boolean
    private external fun native_rmdir(sessionID: Int, dir_path: String): Boolean
    private external fun native_rename(sessionID: Int, old_path: String, new_path: String): Boolean

    companion object {
        const val KeyLen = 32
        const val ScryptDefaultLogN = 16
        const val DefaultBS = 4096
        external fun createVolume(root_cipher_dir: String, password: CharArray, logN: Int, creator: String): Boolean
        external fun init(root_cipher_dir: String, password: CharArray?, givenHash: ByteArray?, returnedHash: ByteArray?): Int
        external fun changePassword(root_cipher_dir: String, old_password: CharArray?, givenHash: ByteArray?, new_password: CharArray, returnedHash: ByteArray?): Boolean

        fun isGocryptfsVolume(path: File): Boolean {
            if (path.isDirectory){
                return File(path, ConstValues.gocryptfsConfFilename).isFile
            }
            return false
        }

        init {
            System.loadLibrary("gocryptfs_jni")
        }
    }

    fun close() {
        synchronized(this){
            native_close(sessionID)
        }
    }

    fun isClosed(): Boolean {
        synchronized(this){
            return native_is_closed(sessionID)
        }
    }

    fun listDir(dir_path: String): MutableList<ExplorerElement> {
        synchronized(this){
            return native_list_dir(sessionID, dir_path)
        }
    }

    fun mkdir(dir_path: String): Boolean {
        synchronized(this){
            return native_mkdir(sessionID, dir_path)
        }
    }

    fun rmdir(dir_path: String): Boolean {
        synchronized(this){
            return native_rmdir(sessionID, dir_path)
        }
    }

    fun removeFile(file_path: String): Boolean {
        synchronized(this){
            return native_remove_file(sessionID, file_path)
        }
    }

    fun pathExists(file_path: String): Boolean {
        synchronized(this){
            return native_path_exists(sessionID, file_path)
        }
    }

    fun getSize(file_path: String): Long {
        synchronized(this){
            return native_get_size(sessionID, file_path)
        }
    }

    fun closeFile(handleID: Int) {
        synchronized(this){
            native_close_file(sessionID, handleID)
        }
    }

    fun openReadMode(file_path: String): Int {
        synchronized(this){
            return native_open_read_mode(sessionID, file_path)
        }
    }

    fun openWriteMode(file_path: String): Int {
        synchronized(this){
            return native_open_write_mode(sessionID, file_path)
        }
    }

    fun readFile(handleID: Int, offset: Long, buff: ByteArray): Int {
        synchronized(this){
            return native_read_file(sessionID, handleID, offset, buff)
        }
    }

    fun writeFile(handleID: Int, offset: Long, buff: ByteArray, buff_size: Int): Int {
        synchronized(this){
            return native_write_file(sessionID, handleID, offset, buff, buff_size)
        }
    }

    fun truncate(file_path: String, offset: Long): Boolean {
        synchronized(this) {
            return native_truncate(sessionID, file_path, offset)
        }
    }

    fun rename(old_path: String, new_path: String): Boolean {
        synchronized(this) {
            return native_rename(sessionID, old_path, new_path)
        }
    }

    fun exportFile(handleID: Int, os: OutputStream): Boolean {
        var offset: Long = 0
        val ioBuffer = ByteArray(DefaultBS)
        var length: Int
        while (readFile(handleID, offset, ioBuffer).also { length = it } > 0){
            os.write(ioBuffer, 0, length)
            offset += length.toLong()
        }
        os.close()
        return true
    }

    fun exportFile(src_path: String, os: OutputStream): Boolean {
        var success = false
        val srcHandleId = openReadMode(src_path)
        if (srcHandleId != -1) {
            success = exportFile(srcHandleId, os)
            closeFile(srcHandleId)
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

    fun importFile(inputStream: InputStream, handleID: Int): Boolean {
        var offset: Long = 0
        val ioBuffer = ByteArray(DefaultBS)
        var length: Int
        while (inputStream.read(ioBuffer).also { length = it } > 0) {
            val written = writeFile(handleID, offset, ioBuffer, length).toLong()
            if (written == length.toLong()) {
                 offset += written
            } else {
                inputStream.close()
                return false
            }
        }
        closeFile(handleID)
        inputStream.close()
        return true
    }

    fun importFile(inputStream: InputStream, dst_path: String): Boolean {
        var success = false
        val dstHandleId = openWriteMode(dst_path)
        if (dstHandleId != -1) {
            success = importFile(inputStream, dstHandleId)
            closeFile(dstHandleId)
        }
        return success
    }

    fun importFile(context: Context, src_uri: Uri, dst_path: String): Boolean {
        val inputStream = context.contentResolver.openInputStream(src_uri)
        if (inputStream != null){
            return importFile(inputStream, dst_path)
        }
        return false
    }

    fun recursiveMapFiles(rootPath: String): MutableList<ExplorerElement> {
        val result = mutableListOf<ExplorerElement>()
        val explorerElements = listDir(rootPath)
        result.addAll(explorerElements)
        for (e in explorerElements){
            if (e.isDirectory){
                result.addAll(recursiveMapFiles(e.fullPath))
            }
        }
        return result
    }

    fun recursiveRemoveDirectory(plain_directory_path: String): String? {
        val explorerElements = listDir(plain_directory_path)
        for (e in explorerElements) {
            val fullPath = PathUtils.pathJoin(plain_directory_path, e.name)
            if (e.isDirectory) {
                val result = recursiveRemoveDirectory(fullPath)
                result?.let { return it }
            } else {
                if (!removeFile(fullPath)) {
                    return fullPath
                }
            }
        }
        return if (!rmdir(plain_directory_path)) {
            plain_directory_path
        } else {
            null
        }
    }
}
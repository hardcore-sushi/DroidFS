package sushi.hardcore.droidfs

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.*
import androidx.documentfile.provider.DocumentFile
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.file_operations.OperationFile
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.Wiper
import java.io.File
import java.io.FileNotFoundException

class FileOperationService : Service() {

    private val binder = LocalBinder()
    private lateinit var gocryptfsVolume: GocryptfsVolume

    inner class LocalBinder : Binder() {
        fun getService(): FileOperationService = this@FileOperationService
        fun setGocryptfsVolume(g: GocryptfsVolume) {
            gocryptfsVolume = g
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    private fun copyFile(srcPath: String, dstPath: String, remoteGocryptfsVolume: GocryptfsVolume = gocryptfsVolume): Boolean {
        var success = true
        val srcHandleId = remoteGocryptfsVolume.openReadMode(srcPath)
        if (srcHandleId != -1){
            val dstHandleId = gocryptfsVolume.openWriteMode(dstPath)
            if (dstHandleId != -1){
                var offset: Long = 0
                val ioBuffer = ByteArray(GocryptfsVolume.DefaultBS)
                var length: Int
                while (remoteGocryptfsVolume.readFile(srcHandleId, offset, ioBuffer).also { length = it } > 0) {
                    val written = gocryptfsVolume.writeFile(dstHandleId, offset, ioBuffer, length).toLong()
                    if (written == length.toLong()) {
                        offset += written
                    } else {
                        success = false
                        break
                    }
                }
                gocryptfsVolume.closeFile(dstHandleId)
            } else {
                success = false
            }
            remoteGocryptfsVolume.closeFile(srcHandleId)
        } else {
            success = false
        }
        return success
    }

    fun copyElements(items: ArrayList<OperationFile>, remoteGocryptfsVolume: GocryptfsVolume = gocryptfsVolume, callback: (String?) -> Unit){
        Thread {
            var failedItem: String? = null
            for (item in items){
                if (item.explorerElement.isDirectory){
                    if (!gocryptfsVolume.pathExists(item.dstPath!!)) {
                        if (!gocryptfsVolume.mkdir(item.dstPath!!)) {
                            failedItem = item.explorerElement.fullPath
                        }
                    }
                } else {
                    if (!copyFile(item.explorerElement.fullPath, item.dstPath!!, remoteGocryptfsVolume)){
                        failedItem = item.explorerElement.fullPath
                    }
                }
                if (failedItem != null){
                    break
                }
            }
            callback(failedItem)
        }.start()
    }

    fun moveElements(items: ArrayList<OperationFile>, callback: (String?) -> Unit){
        Thread {
            val mergedFolders = ArrayList<String>()
            var failedItem: String? = null
            for (item in items){
                if (item.explorerElement.isDirectory && gocryptfsVolume.pathExists(item.dstPath!!)){ //folder will be merged
                    mergedFolders.add(item.explorerElement.fullPath)
                } else {
                    if (!gocryptfsVolume.rename(item.explorerElement.fullPath, item.dstPath!!)){
                        failedItem = item.explorerElement.fullPath
                        break
                    }
                }
            }
            if (failedItem == null){
                for (path in mergedFolders) {
                    if (!gocryptfsVolume.rmdir(path)){
                        failedItem = path
                        break
                    }
                }
            }
            callback(failedItem)
        }.start()
    }

    fun importFilesFromUris(items: ArrayList<OperationFile>, uris: List<Uri>, callback: (String?) -> Unit){
        Thread {
            var failedIndex = -1
            for (i in 0 until items.size) {
                try {
                    if (!gocryptfsVolume.importFile(this, uris[i], items[i].dstPath!!)){
                        failedIndex = i
                    }
                } catch (e: FileNotFoundException){
                    failedIndex = i
                }
                if (failedIndex != -1){
                    callback(uris[failedIndex].toString())
                    break
                }
            }
            if (failedIndex == -1){
                callback(null)
            }
        }.start()
    }

    fun wipeUris(uris: List<Uri>, callback: (String?) -> Unit){
        Thread {
            var errorMsg: String? = null
            for (uri in uris) {
                errorMsg = Wiper.wipe(this, uri)
                if (errorMsg != null) {
                    break
                }
            }
            callback(errorMsg)
        }.start()
    }

    private fun exportFileInto(srcPath: String, treeDocumentFile: DocumentFile): Boolean {
        val outputStream = treeDocumentFile.createFile("*/*", File(srcPath).name)?.uri?.let {
            contentResolver.openOutputStream(it)
        }
        return if (outputStream != null){
            gocryptfsVolume.exportFile(srcPath, outputStream)
        } else {
            false
        }
    }

    private fun recursiveExportDirectory(plain_directory_path: String, treeDocumentFile: DocumentFile): String? {
        treeDocumentFile.createDirectory(File(plain_directory_path).name)?.let { childTree ->
            val explorerElements = gocryptfsVolume.listDir(plain_directory_path)
            for (e in explorerElements) {
                val fullPath = PathUtils.pathJoin(plain_directory_path, e.name)
                if (e.isDirectory) {
                    val failedItem = recursiveExportDirectory(fullPath, childTree)
                    failedItem?.let { return it }
                } else {
                    if (!exportFileInto(fullPath, childTree)){
                        return fullPath
                    }
                }
            }
            return null
        }
        return treeDocumentFile.name
    }

    fun exportFiles(uri: Uri, items: List<ExplorerElement>, callback: (String?) -> Unit){
        Thread {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            DocumentFile.fromTreeUri(this, uri)?.let { treeDocumentFile ->
                var failedItem: String? = null
                for (element in items) {
                    failedItem = if (element.isDirectory) {
                        recursiveExportDirectory(element.fullPath, treeDocumentFile)
                    } else {
                        if (exportFileInto(element.fullPath, treeDocumentFile)) null else element.fullPath
                    }
                    if (failedItem != null) {
                        break
                    }
                }
                callback(failedItem)
            }
        }.start()
    }
}
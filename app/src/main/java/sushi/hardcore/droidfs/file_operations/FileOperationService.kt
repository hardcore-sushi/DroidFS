package sushi.hardcore.droidfs.file_operations

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.*
import androidx.documentfile.provider.DocumentFile
import sushi.hardcore.droidfs.GocryptfsVolume
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.Wiper
import java.io.File
import java.io.FileNotFoundException

class FileOperationService : Service() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "FileOperations"
        const val ACTION_CANCEL = "file_operation_cancel"
    }

    private val binder = LocalBinder()
    private lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var notificationManager: NotificationManager
    private var notifications = HashMap<Int, Boolean>()
    private var lastNotificationId = 0

    inner class LocalBinder : Binder() {
        fun getService(): FileOperationService = this@FileOperationService
        fun setGocryptfsVolume(g: GocryptfsVolume) {
            gocryptfsVolume = g
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    private fun showNotification(message: Int, total: Int?): FileOperationNotification {
        ++lastNotificationId
        if (!::notificationManager.isInitialized){
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.file_operations), NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val cancelIntent = Intent(this, NotificationBroadcastReceiver::class.java).apply {
            val bundle = Bundle()
            bundle.putBinder("binder", LocalBinder())
            bundle.putInt("notificationId", lastNotificationId)
            putExtra("bundle", bundle)
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.icon_close),
                    getString(R.string.cancel),
                    cancelPendingIntent
            )
        } else {
            Notification.Action.Builder(
                    R.drawable.icon_close,
                    getString(R.string.cancel),
                    cancelPendingIntent
            )
        }
        notificationBuilder
                .setContentTitle(getString(message))
                .setSmallIcon(R.mipmap.icon_launcher)
                .setOngoing(true)
                .addAction(notificationAction.build())
        if (total != null) {
            notificationBuilder
                .setContentText("0/$total")
                .setProgress(total, 0, false)
        } else {
            notificationBuilder
                .setContentText(getString(R.string.discovering_files))
                .setProgress(0, 0, true)
        }
        notifications[lastNotificationId] = false
        notificationManager.notify(lastNotificationId, notificationBuilder.build())
        return FileOperationNotification(notificationBuilder, lastNotificationId)
    }

    private fun updateNotificationProgress(notification: FileOperationNotification, progress: Int, total: Int){
        notification.notificationBuilder
                .setProgress(total, progress, false)
                .setContentText("$progress/$total")
        notificationManager.notify(notification.notificationId, notification.notificationBuilder.build())
    }

    private fun cancelNotification(notification: FileOperationNotification){
        notificationManager.cancel(notification.notificationId)
    }

    fun cancelOperation(notificationId: Int){
        notifications[notificationId] = true
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
            val notification = showNotification(R.string.file_op_copy_msg, items.size)
            var failedItem: String? = null
            for (i in 0 until items.size){
                if (notifications[notification.notificationId]!!){
                    cancelNotification(notification)
                    return@Thread
                }
                if (items[i].explorerElement.isDirectory){
                    if (!gocryptfsVolume.pathExists(items[i].dstPath!!)) {
                        if (!gocryptfsVolume.mkdir(items[i].dstPath!!)) {
                            failedItem = items[i].explorerElement.fullPath
                        }
                    }
                } else {
                    if (!copyFile(items[i].explorerElement.fullPath, items[i].dstPath!!, remoteGocryptfsVolume)){
                        failedItem = items[i].explorerElement.fullPath
                    }
                }
                if (failedItem == null){
                    updateNotificationProgress(notification, i, items.size)
                } else {
                    break
                }
            }
            cancelNotification(notification)
            callback(failedItem)
        }.start()
    }

    fun moveElements(items: ArrayList<OperationFile>, callback: (String?) -> Unit){
        Thread {
            val notification = showNotification(R.string.file_op_move_msg, items.size)
            val mergedFolders = ArrayList<String>()
            var failedItem: String? = null
            for (i in 0 until items.size){
                if (notifications[notification.notificationId]!!){
                    cancelNotification(notification)
                    return@Thread
                }
                if (items[i].explorerElement.isDirectory && gocryptfsVolume.pathExists(items[i].dstPath!!)){ //folder will be merged
                    mergedFolders.add(items[i].explorerElement.fullPath)
                } else {
                    if (!gocryptfsVolume.rename(items[i].explorerElement.fullPath, items[i].dstPath!!)){
                        failedItem = items[i].explorerElement.fullPath
                        break
                    } else {
                        updateNotificationProgress(notification, i, items.size)
                    }
                }
            }
            if (failedItem == null){
                for (i in 0 until mergedFolders.size) {
                    if (notifications[notification.notificationId]!!){
                        cancelNotification(notification)
                        return@Thread
                    }
                    if (!gocryptfsVolume.rmdir(mergedFolders[i])){
                        failedItem = mergedFolders[i]
                        break
                    } else {
                        updateNotificationProgress(notification, items.size-(mergedFolders.size-i), items.size)
                    }
                }
            }
            cancelNotification(notification)
            callback(failedItem)
        }.start()
    }

    private fun importFilesFromUris(dstPaths: List<String>, uris: List<Uri>, reuseNotification: FileOperationNotification? = null, callback: (String?) -> Unit){
        val notification = reuseNotification ?: showNotification(R.string.file_op_import_msg, dstPaths.size)
        var failedIndex = -1
        for (i in dstPaths.indices) {
            if (notifications[notification.notificationId]!!){
                cancelNotification(notification)
                return
            }
            try {
                if (!gocryptfsVolume.importFile(this, uris[i], dstPaths[i])) {
                    failedIndex = i
                }
            } catch (e: FileNotFoundException){
                failedIndex = i
            }
            if (failedIndex == -1) {
                updateNotificationProgress(notification, i, dstPaths.size)
            } else {
                cancelNotification(notification)
                callback(uris[failedIndex].toString())
                break
            }
        }
        if (failedIndex == -1){
            cancelNotification(notification)
            callback(null)
        }
    }

    fun importFilesFromUris(dstPaths: List<String>, uris: List<Uri>, callback: (String?) -> Unit) {
        Thread {
            importFilesFromUris(dstPaths, uris, null, callback)
        }.start()
    }

    /**
     * Map the content of an unencrypted directory to prepare its import
     *
     * Contents of dstFiles and srcUris, at the same index, will match each other
     *
     * @return false if cancelled early, true otherwise.
     */
    private fun recursiveMapDirectoryForImport(
        rootSrcDir: DocumentFile,
        rootDstPath: String,
        dstFiles: ArrayList<String>,
        srcUris: ArrayList<Uri>,
        dstDirs: ArrayList<String>,
        notification: FileOperationNotification
    ): Boolean {
        dstDirs.add(rootDstPath)
        for (child in rootSrcDir.listFiles()) {
            if (notifications[notification.notificationId]!!) {
                cancelNotification(notification)
                return false
            }
            child.name?.let { name ->
                val subPath = PathUtils.pathJoin(rootDstPath, name)
                if (child.isDirectory) {
                    if (!recursiveMapDirectoryForImport(child, subPath, dstFiles, srcUris, dstDirs, notification)) {
                        return false
                    }
                }
                else if (child.isFile) {
                    srcUris.add(child.uri)
                    dstFiles.add(subPath)
                }
            }
        }
        return true
    }

    fun importDirectory(rootDstPath: String, rootSrcDir: DocumentFile, callback: (String?, List<Uri>) -> Unit) {
        Thread {
            val notification = showNotification(R.string.file_op_import_msg, null)

            val dstFiles = arrayListOf<String>()
            val srcUris = arrayListOf<Uri>()
            val dstDirs = arrayListOf<String>()
            if (!recursiveMapDirectoryForImport(rootSrcDir, rootDstPath, dstFiles, srcUris, dstDirs, notification)) {
                return@Thread
            }

            updateNotificationProgress(notification, 0, dstDirs.size)

            // create destination folders so the new files can use them
            for (mkdir in dstDirs) {
                if (notifications[notification.notificationId]!!) {
                    cancelNotification(notification)
                    return@Thread
                }
                gocryptfsVolume.mkdir(mkdir)
            }

            importFilesFromUris(dstFiles, srcUris, notification) { failedItem ->
                callback(failedItem, srcUris)
            }
        }.start()
    }

    fun wipeUris(uris: List<Uri>, callback: (String?) -> Unit){
        Thread {
            val notification = showNotification(R.string.file_op_wiping_msg, uris.size)
            var errorMsg: String? = null
            for (i in uris.indices) {
                if (notifications[notification.notificationId]!!){
                    cancelNotification(notification)
                    return@Thread
                }
                errorMsg = Wiper.wipe(this, uris[i])
                if (errorMsg == null) {
                    updateNotificationProgress(notification, i, uris.size)
                } else {
                    break
                }
            }
            cancelNotification(notification)
            callback(errorMsg)
        }.start()
    }

    private fun exportFileInto(srcPath: String, treeDocumentFile: DocumentFile): Boolean {
        val outputStream = treeDocumentFile.createFile("*/*", File(srcPath).name)?.uri?.let {
            contentResolver.openOutputStream(it)
        }
        return if (outputStream == null) {
            false
        } else {
            gocryptfsVolume.exportFile(srcPath, outputStream)
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
                val notification = showNotification(R.string.file_op_export_msg, items.size)
                var failedItem: String? = null
                for (i in items.indices) {
                    if (notifications[notification.notificationId]!!){
                        cancelNotification(notification)
                        return@Thread
                    }
                    failedItem = if (items[i].isDirectory) {
                        recursiveExportDirectory(items[i].fullPath, treeDocumentFile)
                    } else {
                        if (exportFileInto(items[i].fullPath, treeDocumentFile)) null else items[i].fullPath
                    }
                    if (failedItem == null) {
                        updateNotificationProgress(notification, i, items.size)
                    } else {
                        break
                    }
                }
                cancelNotification(notification)
                callback(failedItem)
            }
        }.start()
    }
}
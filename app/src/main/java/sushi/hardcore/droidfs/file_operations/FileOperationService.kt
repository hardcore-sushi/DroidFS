package sushi.hardcore.droidfs.file_operations

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.ObjRef
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
    private lateinit var encryptedVolume: EncryptedVolume
    private lateinit var notificationManager: NotificationManagerCompat
    private val tasks = HashMap<Int, Job>()
    private var lastNotificationId = 0

    inner class LocalBinder : Binder() {
        fun getService(): FileOperationService = this@FileOperationService
        fun setEncryptedVolume(volume: EncryptedVolume) {
            encryptedVolume = volume
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    private fun showNotification(message: Int, total: Int?): FileOperationNotification {
        ++lastNotificationId
        if (!::notificationManager.isInitialized){
            notificationManager = NotificationManagerCompat.from(this)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.file_operations),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        notificationBuilder
                .setContentTitle(getString(message))
                .setSmallIcon(R.mipmap.icon_launcher)
                .setOngoing(true)
                .addAction(NotificationCompat.Action(
                    R.drawable.icon_close,
                    getString(R.string.cancel),
                    PendingIntent.getBroadcast(
                        this,
                        0,
                        Intent(this, NotificationBroadcastReceiver::class.java).apply {
                            val bundle = Bundle()
                            bundle.putBinder("binder", LocalBinder())
                            bundle.putInt("notificationId", lastNotificationId)
                            putExtra("bundle", bundle)
                            action = ACTION_CANCEL
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ))
        if (total != null) {
            notificationBuilder
                .setContentText("0/$total")
                .setProgress(total, 0, false)
        } else {
            notificationBuilder
                .setContentText(getString(R.string.discovering_files))
                .setProgress(0, 0, true)
        }
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
        tasks[notificationId]?.cancel()
    }

    open class TaskResult<T>(val cancelled: Boolean, val failedItem: T?)

    private suspend fun <T> waitForTask(notification: FileOperationNotification, task: Deferred<T>): TaskResult<T> {
        tasks[notification.notificationId] = task
        return try {
            TaskResult(false, task.await())
        } catch (e: CancellationException) {
            TaskResult(true, null)
        } finally {
            cancelNotification(notification)
        }
    }

    private fun copyFile(srcPath: String, dstPath: String, remoteEncryptedVolume: EncryptedVolume = encryptedVolume): Boolean {
        var success = true
        val srcFileHandle = remoteEncryptedVolume.openFile(srcPath)
        if (srcFileHandle != -1L) {
            val dstFileHandle = encryptedVolume.openFile(dstPath)
            if (dstFileHandle != -1L) {
                var offset: Long = 0
                val ioBuffer = ByteArray(ConstValues.IO_BUFF_SIZE)
                var length: Int
                while (remoteEncryptedVolume.read(srcFileHandle, ioBuffer, offset).also { length = it } > 0) {
                    val written = encryptedVolume.write(dstFileHandle, offset, ioBuffer, length).toLong()
                    if (written == length.toLong()) {
                        offset += written
                    } else {
                        success = false
                        break
                    }
                }
                encryptedVolume.closeFile(dstFileHandle)
            } else {
                success = false
            }
            remoteEncryptedVolume.closeFile(srcFileHandle)
        } else {
            success = false
        }
        return success
    }

    suspend fun copyElements(
        items: ArrayList<OperationFile>,
        remoteEncryptedVolume: EncryptedVolume = encryptedVolume
    ): String? = coroutineScope {
        val notification = showNotification(R.string.file_op_copy_msg, items.size)
        val task = async {
            var failedItem: String? = null
            for (i in 0 until items.size) {
                withContext(Dispatchers.IO) {
                    if (items[i].isDirectory) {
                        if (!encryptedVolume.pathExists(items[i].dstPath!!)) {
                            if (!encryptedVolume.mkdir(items[i].dstPath!!)) {
                                failedItem = items[i].srcPath
                            }
                        }
                    } else {
                        if (!copyFile(items[i].srcPath, items[i].dstPath!!, remoteEncryptedVolume)) {
                            failedItem = items[i].srcPath
                        }
                    }
                }
                if (failedItem == null) {
                    updateNotificationProgress(notification, i+1, items.size)
                } else {
                    break
                }
            }
            failedItem
        }
        // treat cancellation as success
        waitForTask(notification, task).failedItem
    }

    suspend fun moveElements(toMove: List<OperationFile>, toClean: List<String>): String? = coroutineScope {
        val notification = showNotification(R.string.file_op_move_msg, toMove.size)
        val task = async(Dispatchers.IO) {
            val total = toMove.size+toClean.size
            var failedItem: String? = null
            for ((i, item) in toMove.withIndex()) {
                if (!encryptedVolume.rename(item.srcPath, item.dstPath!!)) {
                    failedItem = item.srcPath
                    break
                } else {
                    updateNotificationProgress(notification, i+1, total)
                }
            }
            if (failedItem == null) {
                for ((i, folderPath) in toClean.asReversed().withIndex()) {
                    if (!encryptedVolume.rmdir(folderPath)) {
                        failedItem = folderPath
                        break
                    } else {
                        updateNotificationProgress(notification, toMove.size+i+1, total)
                    }
                }
            }
            failedItem
        }
        // treat cancellation as success
        waitForTask(notification, task).failedItem
    }

    private suspend fun importFilesFromUris(
        dstPaths: List<String>,
        uris: List<Uri>,
        notification: FileOperationNotification,
    ): String? {
        var failedIndex = -1
        for (i in dstPaths.indices) {
            withContext(Dispatchers.IO) {
                try {
                    if (!encryptedVolume.importFile(this@FileOperationService, uris[i], dstPaths[i])) {
                        failedIndex = i
                    }
                } catch (e: FileNotFoundException) {
                    failedIndex = i
                }
            }
            if (failedIndex == -1) {
                updateNotificationProgress(notification, i+1, dstPaths.size)
            } else {
                return uris[failedIndex].toString()
            }
        }
        return null
    }

    suspend fun importFilesFromUris(dstPaths: List<String>, uris: List<Uri>): TaskResult<String?> = coroutineScope {
        val notification = showNotification(R.string.file_op_import_msg, dstPaths.size)
        val task = async {
            importFilesFromUris(dstPaths, uris, notification)
        }
        waitForTask(notification, task)
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
        scope: CoroutineScope,
    ): Boolean {
        dstDirs.add(rootDstPath)
        for (child in rootSrcDir.listFiles()) {
            if (!scope.isActive) {
                return false
            }
            child.name?.let { name ->
                val subPath = PathUtils.pathJoin(rootDstPath, name)
                if (child.isDirectory) {
                    if (!recursiveMapDirectoryForImport(child, subPath, dstFiles, srcUris, dstDirs, scope)) {
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

    class ImportDirectoryResult(val taskResult: TaskResult<String?>, val uris: List<Uri>)

    suspend fun importDirectory(
        rootDstPath: String,
        rootSrcDir: DocumentFile,
    ): ImportDirectoryResult = coroutineScope {
        val notification = showNotification(R.string.file_op_import_msg, null)
        val srcUris = arrayListOf<Uri>()
        val task = async {
            var failedItem: String? = null
            val dstFiles = arrayListOf<String>()
            val dstDirs = arrayListOf<String>()

            withContext(Dispatchers.IO) {
                if (!recursiveMapDirectoryForImport(rootSrcDir, rootDstPath, dstFiles, srcUris, dstDirs, this)) {
                    return@withContext
                }

                // create destination folders so the new files can use them
                for (dir in dstDirs) {
                    if (!encryptedVolume.mkdir(dir)) {
                        failedItem = dir
                        break
                    }
                }
            }
            if (failedItem == null) {
                failedItem = importFilesFromUris(dstFiles, srcUris, notification)
            }
            failedItem
        }
        ImportDirectoryResult(waitForTask(notification, task), srcUris)
    }

    suspend fun wipeUris(uris: List<Uri>, rootFile: DocumentFile? = null): String? = coroutineScope {
        val notification = showNotification(R.string.file_op_wiping_msg, uris.size)
        val task = async {
            var errorMsg: String? = null
            for (i in uris.indices) {
                withContext(Dispatchers.IO) {
                    errorMsg = Wiper.wipe(this@FileOperationService, uris[i])
                }
                if (errorMsg == null) {
                    updateNotificationProgress(notification, i+1, uris.size)
                } else {
                    break
                }
            }
            if (errorMsg == null) {
                rootFile?.delete()
            }
            errorMsg
        }
        // treat cancellation as success
        waitForTask(notification, task).failedItem
    }

    private fun exportFileInto(srcPath: String, treeDocumentFile: DocumentFile): Boolean {
        val outputStream = treeDocumentFile.createFile("*/*", File(srcPath).name)?.uri?.let {
            contentResolver.openOutputStream(it)
        }
        return if (outputStream == null) {
            false
        } else {
            encryptedVolume.exportFile(srcPath, outputStream)
        }
    }

    private fun recursiveExportDirectory(
        plain_directory_path: String,
        treeDocumentFile: DocumentFile,
        scope: CoroutineScope
    ): String? {
        treeDocumentFile.createDirectory(File(plain_directory_path).name)?.let { childTree ->
            val explorerElements = encryptedVolume.readDir(plain_directory_path) ?: return null
            for (e in explorerElements) {
                if (!scope.isActive) {
                    return null
                }
                val fullPath = PathUtils.pathJoin(plain_directory_path, e.name)
                if (e.isDirectory) {
                    val failedItem = recursiveExportDirectory(fullPath, childTree, scope)
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

    suspend fun exportFiles(uri: Uri, items: List<ExplorerElement>): TaskResult<String?> = coroutineScope {
        val notification = showNotification(R.string.file_op_export_msg, items.size)
        val task = async {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val treeDocumentFile = DocumentFile.fromTreeUri(this@FileOperationService, uri)!!
            var failedItem: String? = null
            for (i in items.indices) {
                withContext(Dispatchers.IO) {
                    failedItem = if (items[i].isDirectory) {
                        recursiveExportDirectory(items[i].fullPath, treeDocumentFile, this)
                    } else {
                        if (exportFileInto(items[i].fullPath, treeDocumentFile)) null else items[i].fullPath
                    }
                }
                if (failedItem == null) {
                    updateNotificationProgress(notification, i+1, items.size)
                } else {
                    break
                }
            }
            failedItem
        }
        waitForTask(notification, task)
    }

    private fun recursiveCountChildElements(rootDirectory: DocumentFile, scope: CoroutineScope): Int {
        if (!scope.isActive) {
            return 0
        }
        val children = rootDirectory.listFiles()
        var count = children.size
        for (child in children) {
            if (child.isDirectory) {
                count += recursiveCountChildElements(child, scope)
            }
        }
        return count
    }

    private fun recursiveCopyVolume(
        src: DocumentFile,
        dst: DocumentFile,
        dstRootDirectory: ObjRef<DocumentFile?>?,
        notification: FileOperationNotification,
        total: Int,
        scope: CoroutineScope,
        progress: ObjRef<Int> = ObjRef(0)
    ): DocumentFile? {
        val dstDir = dst.createDirectory(src.name ?: return src) ?: return src
        dstRootDirectory?.let { it.value = dstDir }
        for (child in src.listFiles()) {
            if (!scope.isActive) {
                return null
            }
            if (child.isFile) {
                val dstFile = dstDir.createFile("", child.name ?: return child) ?: return child
                val outputStream = contentResolver.openOutputStream(dstFile.uri)
                val inputStream = contentResolver.openInputStream(child.uri)
                if (outputStream == null || inputStream == null) return child
                val written = inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()
                if (written != child.length()) return child
            } else {
                recursiveCopyVolume(child, dstDir, null, notification, total, scope, progress)?.let { return it }
            }
            progress.value++
            updateNotificationProgress(notification, progress.value, total)
        }
        return null
    }

    class CopyVolumeResult(val taskResult: TaskResult<DocumentFile?>, val dstRootDirectory: DocumentFile?)

    suspend fun copyVolume(src: DocumentFile, dst: DocumentFile): CopyVolumeResult = coroutineScope {
        val notification = showNotification(R.string.copy_volume_notification, null)
        val dstRootDirectory = ObjRef<DocumentFile?>(null)
        val task = async(Dispatchers.IO) {
            val total = recursiveCountChildElements(src, this)
            if (isActive) {
                updateNotificationProgress(notification, 0, total)
                recursiveCopyVolume(src, dst, dstRootDirectory, notification, total, this)
            } else {
                null
            }
        }
        // treat cancellation as success
        CopyVolumeResult(waitForTask(notification, task), dstRootDirectory.value)
    }
}
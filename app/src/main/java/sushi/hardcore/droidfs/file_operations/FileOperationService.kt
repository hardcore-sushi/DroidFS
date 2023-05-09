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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.VolumeManager
import sushi.hardcore.droidfs.VolumeManagerApp
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
    private lateinit var volumeManger: VolumeManager
    private var serviceScope = MainScope()
    private lateinit var notificationManager: NotificationManagerCompat
    private val tasks = HashMap<Int, Job>()
    private var lastNotificationId = 0

    inner class LocalBinder : Binder() {
        fun getService(): FileOperationService = this@FileOperationService
    }

    override fun onBind(p0: Intent?): IBinder {
        volumeManger = (application as VolumeManagerApp).volumeManager
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
                .setSmallIcon(R.drawable.ic_notification)
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
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

    private fun getEncryptedVolume(volumeId: Int): EncryptedVolume {
        return volumeManger.getVolume(volumeId) ?: throw IllegalArgumentException("Invalid volumeId: $volumeId")
    }

    private suspend fun <T> waitForTask(notification: FileOperationNotification, task: Deferred<T>): TaskResult<out T> {
        tasks[notification.notificationId] = task
        return serviceScope.async {
            try {
                TaskResult.completed(task.await())
            } catch (e: CancellationException) {
                TaskResult.cancelled()
            } catch (e: Throwable) {
                e.printStackTrace()
                TaskResult.error(e.localizedMessage)
            } finally {
                cancelNotification(notification)
            }
        }.await()
    }

    private suspend fun <T> volumeTask(
        volumeId: Int,
        notification: FileOperationNotification,
        task: suspend (encryptedVolume: EncryptedVolume) -> T
    ): TaskResult<out T> {
        return waitForTask(
            notification,
            volumeManger.getCoroutineScope(volumeId).async {
                task(getEncryptedVolume(volumeId))
            }
        )
    }

    private suspend fun copyFile(
        encryptedVolume: EncryptedVolume,
        srcPath: String,
        dstPath: String,
        srcEncryptedVolume: EncryptedVolume = encryptedVolume,
    ): Boolean {
        var success = true
        val srcFileHandle = srcEncryptedVolume.openFileReadMode(srcPath)
        if (srcFileHandle != -1L) {
            val dstFileHandle = encryptedVolume.openFileWriteMode(dstPath)
            if (dstFileHandle != -1L) {
                var offset: Long = 0
                val ioBuffer = ByteArray(Constants.IO_BUFF_SIZE)
                var length: Long
                while (srcEncryptedVolume.read(srcFileHandle, offset, ioBuffer, 0, ioBuffer.size.toLong()).also { length = it.toLong() } > 0) {
                    yield()
                    val written = encryptedVolume.write(dstFileHandle, offset, ioBuffer, 0, length).toLong()
                    if (written == length) {
                        offset += written
                    } else {
                        success = false
                        break
                    }
                }
                encryptedVolume.truncate(dstPath, offset)
                encryptedVolume.closeFile(dstFileHandle)
            } else {
                success = false
            }
            srcEncryptedVolume.closeFile(srcFileHandle)
        } else {
            success = false
        }
        return success
    }

    suspend fun copyElements(
        volumeId: Int,
        items: List<OperationFile>,
        srcVolumeId: Int = volumeId,
    ): TaskResult<out String?> {
        val notification = showNotification(R.string.file_op_copy_msg, items.size)
        val srcEncryptedVolume = getEncryptedVolume(srcVolumeId)
        return volumeTask(volumeId, notification) { encryptedVolume ->
            var failedItem: String? = null
            for (i in items.indices) {
                yield()
                if (items[i].isDirectory) {
                    if (!encryptedVolume.pathExists(items[i].dstPath!!)) {
                        if (!encryptedVolume.mkdir(items[i].dstPath!!)) {
                            failedItem = items[i].srcPath
                        }
                    }
                } else if (!copyFile(encryptedVolume, items[i].srcPath, items[i].dstPath!!, srcEncryptedVolume)) {
                    failedItem = items[i].srcPath
                }
                if (failedItem == null) {
                    updateNotificationProgress(notification, i+1, items.size)
                } else {
                    break
                }
            }
            failedItem
        }
    }

    suspend fun moveElements(volumeId: Int, toMove: List<OperationFile>, toClean: List<String>): TaskResult<out String?> {
        val notification = showNotification(R.string.file_op_move_msg, toMove.size)
        return volumeTask(volumeId, notification) { encryptedVolume ->
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
    }

    private suspend fun importFilesFromUris(
        encryptedVolume: EncryptedVolume,
        dstPaths: List<String>,
        uris: List<Uri>,
        notification: FileOperationNotification,
    ): String? {
        var failedIndex = -1
        for (i in dstPaths.indices) {
            yield()
            try {
                if (!encryptedVolume.importFile(this@FileOperationService, uris[i], dstPaths[i])) {
                    failedIndex = i
                }
            } catch (e: FileNotFoundException) {
                failedIndex = i
            }
            if (failedIndex == -1) {
                updateNotificationProgress(notification, i+1, dstPaths.size)
            } else {
                return uris[failedIndex].toString()
            }
        }
        return null
    }

    suspend fun importFilesFromUris(volumeId: Int, dstPaths: List<String>, uris: List<Uri>): TaskResult<out String?> {
        val notification = showNotification(R.string.file_op_import_msg, dstPaths.size)
        return volumeTask(volumeId, notification) { encryptedVolume ->
            importFilesFromUris(encryptedVolume, dstPaths, uris, notification)
        }
    }

    /**
     * Map the content of an unencrypted directory to prepare its import
     *
     * Contents of dstFiles and srcUris, at the same index, will match each other
     *
     * @return false if cancelled early, true otherwise.
     */
    private suspend fun recursiveMapDirectoryForImport(
        rootSrcDir: DocumentFile,
        rootDstPath: String,
        dstFiles: ArrayList<String>,
        srcUris: ArrayList<Uri>,
        dstDirs: ArrayList<String>,
    ) {
        dstDirs.add(rootDstPath)
        for (child in rootSrcDir.listFiles()) {
            yield()
            child.name?.let { name ->
                val subPath = PathUtils.pathJoin(rootDstPath, name)
                if (child.isDirectory) {
                    recursiveMapDirectoryForImport(child, subPath, dstFiles, srcUris, dstDirs)
                } else if (child.isFile) {
                    srcUris.add(child.uri)
                    dstFiles.add(subPath)
                }
            }
        }
    }

    class ImportDirectoryResult(val taskResult: TaskResult<out String?>, val uris: List<Uri>)

    suspend fun importDirectory(
        volumeId: Int,
        rootDstPath: String,
        rootSrcDir: DocumentFile,
    ): ImportDirectoryResult {
        val notification = showNotification(R.string.file_op_import_msg, null)
        val srcUris = arrayListOf<Uri>()
        return ImportDirectoryResult(volumeTask(volumeId, notification) { encryptedVolume ->
            var failedItem: String? = null
            val dstFiles = arrayListOf<String>()
            val dstDirs = arrayListOf<String>()
            recursiveMapDirectoryForImport(rootSrcDir, rootDstPath, dstFiles, srcUris, dstDirs)
            // create destination folders so the new files can use them
            for (dir in dstDirs) {
                if (!encryptedVolume.mkdir(dir)) {
                    failedItem = dir
                    break
                }
            }
            if (failedItem == null) {
                failedItem = importFilesFromUris(encryptedVolume, dstFiles, srcUris, notification)
            }
            failedItem
        }, srcUris)
    }

    suspend fun wipeUris(uris: List<Uri>, rootFile: DocumentFile? = null): TaskResult<out String?> {
        val notification = showNotification(R.string.file_op_wiping_msg, uris.size)
        val task = serviceScope.async(Dispatchers.IO) {
            var errorMsg: String? = null
            for (i in uris.indices) {
                yield()
                errorMsg = Wiper.wipe(this@FileOperationService, uris[i])
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
        return waitForTask(notification, task)
    }

    private fun exportFileInto(encryptedVolume: EncryptedVolume, srcPath: String, treeDocumentFile: DocumentFile): Boolean {
        val outputStream = treeDocumentFile.createFile("*/*", File(srcPath).name)?.uri?.let {
            contentResolver.openOutputStream(it)
        }
        return if (outputStream == null) {
            false
        } else {
            encryptedVolume.exportFile(srcPath, outputStream)
        }
    }

    private suspend fun recursiveExportDirectory(
        encryptedVolume: EncryptedVolume,
        plain_directory_path: String,
        treeDocumentFile: DocumentFile,
    ): String? {
        treeDocumentFile.createDirectory(File(plain_directory_path).name)?.let { childTree ->
            val explorerElements = encryptedVolume.readDir(plain_directory_path) ?: return null
            for (e in explorerElements) {
                yield()
                val fullPath = PathUtils.pathJoin(plain_directory_path, e.name)
                if (e.isDirectory) {
                    recursiveExportDirectory(encryptedVolume, fullPath, childTree)?.let { return it }
                } else if (!exportFileInto(encryptedVolume, fullPath, childTree)) {
                    return fullPath
                }
            }
            return null
        }
        return treeDocumentFile.name
    }

    suspend fun exportFiles(volumeId: Int, items: List<ExplorerElement>, uri: Uri): TaskResult<out String?> {
        val notification = showNotification(R.string.file_op_export_msg, items.size)
        return volumeTask(volumeId, notification) { encryptedVolume ->
            val treeDocumentFile = DocumentFile.fromTreeUri(this@FileOperationService, uri)!!
            var failedItem: String? = null
            for (i in items.indices) {
                yield()
                failedItem = if (items[i].isDirectory) {
                    recursiveExportDirectory(encryptedVolume, items[i].fullPath, treeDocumentFile)
                } else {
                    if (exportFileInto(encryptedVolume, items[i].fullPath, treeDocumentFile)) {
                        null
                    } else {
                        items[i].fullPath
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
    }

    private suspend fun recursiveRemoveDirectory(encryptedVolume: EncryptedVolume, path: String): String? {
        encryptedVolume.readDir(path)?.let { elements ->
            for (e in elements) {
                yield()
                val fullPath = PathUtils.pathJoin(path, e.name)
                if (e.isDirectory) {
                    recursiveRemoveDirectory(encryptedVolume, fullPath)?.let { return it }
                } else if (!encryptedVolume.deleteFile(fullPath)) {
                    return fullPath
                }
            }
        }
        return if (!encryptedVolume.rmdir(path)) {
            path
        } else {
            null
        }
    }

    suspend fun removeElements(volumeId: Int, items: List<ExplorerElement>): String? {
        val notification = showNotification(R.string.file_op_delete_msg, items.size)
        return volumeTask(volumeId, notification) { encryptedVolume ->
            var failedItem: String? = null
            for ((i, element) in items.withIndex()) {
                yield()
                if (element.isDirectory) {
                    recursiveRemoveDirectory(encryptedVolume, element.fullPath)?.let { failedItem = it }
                } else if (!encryptedVolume.deleteFile(element.fullPath)) {
                    failedItem = element.fullPath
                }
                if (failedItem == null) {
                    updateNotificationProgress(notification, i + 1, items.size)
                } else {
                    break
                }
            }
            failedItem
        }.failedItem // treat cancellation as success
    }

    private suspend fun recursiveCountChildElements(rootDirectory: DocumentFile, scope: CoroutineScope): Int {
        yield()
        val children = rootDirectory.listFiles()
        var count = children.size
        for (child in children) {
            if (child.isDirectory) {
                count += recursiveCountChildElements(child, scope)
            }
        }
        return count
    }

    private suspend fun recursiveCopyVolume(
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
            yield()
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

    class CopyVolumeResult(val taskResult: TaskResult<out DocumentFile?>, val dstRootDirectory: DocumentFile?)

    suspend fun copyVolume(src: DocumentFile, dst: DocumentFile): CopyVolumeResult {
        val notification = showNotification(R.string.copy_volume_notification, null)
        val dstRootDirectory = ObjRef<DocumentFile?>(null)
        val task = serviceScope.async(Dispatchers.IO) {
            val total = recursiveCountChildElements(src, this)
            updateNotificationProgress(notification, 0, total)
            recursiveCopyVolume(src, dst, dstRootDirectory, notification, total, this)
        }
        return CopyVolumeResult(waitForTask(notification, task), dstRootDirectory.value)
    }
}
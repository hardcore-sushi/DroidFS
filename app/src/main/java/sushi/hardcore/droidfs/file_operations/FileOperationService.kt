package sushi.hardcore.droidfs.file_operations

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.NotificationBroadcastReceiver
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.VolumeManager
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.AndroidUtils
import sushi.hardcore.droidfs.util.ObjRef
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Foreground service for file operations.
 *
 * Clients **must** bind to it using the [bind] method.
 *
 * This implementation is not thread-safe. It must only be called from the main UI thread.
 */
class FileOperationService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): FileOperationService = this@FileOperationService
    }

    inner class PendingTask<T>(
        val title: Int,
        val total: Int?,
        private val getTask: (Int) -> Deferred<T>,
        private val onStart: (taskId: Int, job: Deferred<T>) -> Unit,
    ) {
        fun start(taskId: Int): Deferred<T> = getTask(taskId).also { onStart(taskId, it) }
    }

    companion object {
        const val TAG = "FileOperationService"
        const val NOTIFICATION_CHANNEL_ID = "FileOperations"
        const val ACTION_CANCEL = "file_operation_cancel"

        /**
         * Bind to the service.
         *
         * Registers an [ActivityResultLauncher] in the provided activity to request notification permission. Consequently, the activity must not yet be started.
         *
         * The activity must stay running while calling the service's methods.
         *
         * If multiple activities bind simultaneously, only the latest one will be used by the service.
         */
        fun bind(activity: BaseActivity, onBound: (FileOperationService) -> Unit) {
            val helper = AndroidUtils.NotificationPermissionHelper(activity)
            lateinit var service: FileOperationService
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, binder: IBinder) {
                    onBound((binder as FileOperationService.LocalBinder).getService().also {
                        service = it
                        it.notificationPermissionHelpers.addLast(helper)
                    })
                }
                override fun onServiceDisconnected(arg0: ComponentName) {}
            }
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    activity.unbindService(serviceConnection)
                    // Could have been more efficient with a LinkedHashMap but the JDK implementation doesn't allow
                    // to access the latest element in O(1) unless using reflection
                    service.notificationPermissionHelpers.removeAll { it.activity == activity }
                }
            })
            activity.bindService(
                Intent(activity, FileOperationService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    private var isStarted = false
    private val binder = LocalBinder()
    private lateinit var volumeManger: VolumeManager
    private var serviceScope = MainScope()
    private val notificationPermissionHelpers = ArrayDeque<AndroidUtils.NotificationPermissionHelper<BaseActivity>>(2)
    private var askForNotificationPermission = true
    private lateinit var notificationManager: NotificationManagerCompat
    private val notifications = HashMap<Int, NotificationCompat.Builder>()
    private var foregroundNotificationId = -1
    private val tasks = HashMap<Int, Job>()
    private var newTaskId = 1
    private var pendingTask: PendingTask<*>? = null

    override fun onCreate() {
        volumeManger = (application as VolumeManagerApp).volumeManager
    }

    override fun onBind(p0: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPendingTask { id, notification ->
            // on service start, the pending task is the foreground task
            setForeground(id, notification)
        }
        isStarted = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isStarted = false
    }

    private fun processPendingTask() {
        if (isStarted) {
            startPendingTask { id, notification ->
                if (foregroundNotificationId == -1) {
                    // service started but not in foreground yet
                    setForeground(id, notification)
                } else {
                    // already running in foreground, just add a new notification
                    notificationManager.notify(id, notification)
                }
            }
        } else {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FileOperationService::class.java)
            )
        }
    }

    /**
     * Start the pending task and create an associated notification.
     */
    private fun startPendingTask(showNotification: (id: Int, Notification) -> Unit) {
        val task = pendingTask
        pendingTask = null
        if (task == null) {
            Log.w(TAG, "Started without pending task")
            return
        }
        if (!::notificationManager.isInitialized) {
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
            .setContentTitle(getString(task.title))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(NotificationCompat.Action(
                R.drawable.icon_close,
                getString(R.string.cancel),
                PendingIntent.getBroadcast(
                    this,
                    newTaskId,
                    Intent(this, NotificationBroadcastReceiver::class.java).apply {
                        putExtra("bundle", Bundle().apply {
                            putBinder("binder", LocalBinder())
                            putInt("taskId", newTaskId)
                        })
                        action = ACTION_CANCEL
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            ))
        if (task.total != null) {
            notificationBuilder
                .setContentText("0/${task.total}")
                .setProgress(task.total, 0, false)
        } else {
            notificationBuilder
                .setContentText(getString(R.string.discovering_files))
                .setProgress(0, 0, true)
        }
        showNotification(newTaskId, notificationBuilder.build())
        notifications[newTaskId] = notificationBuilder
        tasks[newTaskId] = task.start(newTaskId)
        newTaskId++
    }

    private fun setForeground(id: Int, notification: Notification) {
        ServiceCompat.startForeground(this, id, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
        foregroundNotificationId = id
    }

    private fun updateNotificationProgress(taskId: Int, progress: Int, total: Int) {
        val notificationBuilder = notifications[taskId] ?: return
        notificationBuilder
                .setProgress(total, progress, false)
                .setContentText("$progress/$total")
        notificationManager.notify(taskId, notificationBuilder.build())
    }

    fun cancelOperation(taskId: Int) {
        tasks[taskId]?.cancel()
    }

    private fun getEncryptedVolume(volumeId: Int): EncryptedVolume {
        return volumeManger.getVolume(volumeId) ?: throw IllegalArgumentException("Invalid volumeId: $volumeId")
    }

    /**
     *  Wait on a task, returning the appropriate [TaskResult].
     *
     *  This method also performs cleanup and foreground state management so it must be always used.
     */
    private suspend fun <T> waitForTask(
        taskId: Int,
        task: Deferred<T>,
        onCancelled: (suspend () -> Unit)?,
    ): TaskResult<out T> {
        return coroutineScope {
            withContext(serviceScope.coroutineContext) {
                try {
                    TaskResult.completed(task.await())
                } catch (e: CancellationException) {
                    onCancelled?.invoke()
                    TaskResult.cancelled()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    TaskResult.error(e.localizedMessage)
                } finally {
                    notificationManager.cancel(taskId)
                    notifications.remove(taskId)
                    tasks.remove(taskId)
                    if (tasks.size == 0) {
                        // last task finished, remove from foreground state but don't stop the service
                        ServiceCompat.stopForeground(this@FileOperationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        foregroundNotificationId = -1
                    } else if (taskId == foregroundNotificationId) {
                        // foreground task finished, falling back to the next one
                        val entry = notifications.entries.first()
                        setForeground(entry.key, entry.value.build())
                    }
                }
            }
        }
    }

    /**
     * Create and run a new task until completion.
     *
     * Handles notification permission request, service startup and notification management.
     *
     * Overrides [pendingTask] without checking! (safe if user is not insanely fast)
     */
    private suspend fun <T> newTask(
        title: Int,
        total: Int?,
        getTask: (taskId: Int) -> Deferred<T>,
        onCancelled: (suspend () -> Unit)?,
    ): TaskResult<out T> {
        val startedTask = suspendCoroutine { continuation ->
            val task = PendingTask(title, total, getTask) { taskId, job ->
                continuation.resume(Pair(taskId, job))
            }
            pendingTask = task
            if (askForNotificationPermission) {
                with (notificationPermissionHelpers.last()) {
                    askAndRun { granted ->
                        if (granted) {
                            processPendingTask()
                        } else {
                            CustomAlertDialogBuilder(activity, activity.theme)
                                .setTitle(R.string.warning)
                                .setMessage(R.string.notification_denied_msg)
                                .setPositiveButton(R.string.settings) { _, _ ->
                                    (application as VolumeManagerApp).isStartingExternalApp = true
                                    activity.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null)
                                        )
                                    )
                                }
                                .setNegativeButton(R.string.later, null)
                                .setOnDismissListener { processPendingTask() }
                                .show()
                        }
                    }
                }
                askForNotificationPermission = false // only ask once per service instance
                return@suspendCoroutine
            }
            processPendingTask()
        }
        return waitForTask(startedTask.first, startedTask.second, onCancelled)
    }

    private suspend fun <T> volumeTask(
        title: Int,
        total: Int?,
        volumeId: Int,
        task: suspend (taskId: Int, encryptedVolume: EncryptedVolume) -> T
    ): TaskResult<out T> {
        return newTask(title, total, { taskId ->
            volumeManger.getCoroutineScope(volumeId).async {
                task(taskId, getEncryptedVolume(volumeId))
            }
        }, null)
    }

    private suspend fun <T> globalTask(
        title: Int,
        total: Int?,
        task: suspend (taskId: Int) -> T,
        onCancelled: (suspend () -> Unit)? = null,
    ): TaskResult<out T> {
        return newTask(title, total, { taskId ->
            serviceScope.async(Dispatchers.IO) {
                task(taskId)
            }
        }, if (onCancelled == null) {
            null
        } else {
            {
                serviceScope.launch(Dispatchers.IO) {
                    onCancelled()
                }
            }
        })
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
        val srcEncryptedVolume = getEncryptedVolume(srcVolumeId)
        return volumeTask(R.string.file_op_copy_msg, items.size, volumeId) { taskId, encryptedVolume ->
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
                    updateNotificationProgress(taskId, i+1, items.size)
                } else {
                    break
                }
            }
            failedItem
        }
    }

    suspend fun moveElements(volumeId: Int, toMove: List<OperationFile>, toClean: List<String>): TaskResult<out String?> {
        return volumeTask(R.string.file_op_move_msg, toMove.size, volumeId) { taskId, encryptedVolume ->
            val total = toMove.size+toClean.size
            var failedItem: String? = null
            for ((i, item) in toMove.withIndex()) {
                if (!encryptedVolume.rename(item.srcPath, item.dstPath!!)) {
                    failedItem = item.srcPath
                    break
                } else {
                    updateNotificationProgress(taskId, i+1, total)
                }
            }
            if (failedItem == null) {
                for ((i, folderPath) in toClean.asReversed().withIndex()) {
                    if (!encryptedVolume.rmdir(folderPath)) {
                        failedItem = folderPath
                        break
                    } else {
                        updateNotificationProgress(taskId, toMove.size+i+1, total)
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
        taskId: Int,
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
                updateNotificationProgress(taskId, i+1, dstPaths.size)
            } else {
                return uris[failedIndex].toString()
            }
        }
        return null
    }

    suspend fun importFilesFromUris(volumeId: Int, dstPaths: List<String>, uris: List<Uri>): TaskResult<out String?> {
        return volumeTask(R.string.file_op_import_msg, dstPaths.size, volumeId) { taskId, encryptedVolume ->
            importFilesFromUris(encryptedVolume, dstPaths, uris, taskId)
        }
    }

    /**
     * Map the content of an unencrypted directory to prepare its import
     *
     * Contents of dstFiles and srcUris, at the same index, will match each other
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
        val srcUris = arrayListOf<Uri>()
        return ImportDirectoryResult(volumeTask(R.string.file_op_import_msg, null, volumeId) { taskId, encryptedVolume ->
            var failedItem: String? = null
            val dstFiles = arrayListOf<String>()
            val dstDirs = arrayListOf<String>()
            recursiveMapDirectoryForImport(rootSrcDir, rootDstPath, dstFiles, srcUris, dstDirs)
            // create destination folders so the new files can use them
            for (dir in dstDirs) {
                // if directory creation fails, check if it was already present
                if (!encryptedVolume.mkdir(dir) && encryptedVolume.getAttr(dir)?.type != Stat.S_IFDIR) {
                    failedItem = dir
                    break
                }
            }
            if (failedItem == null) {
                failedItem = importFilesFromUris(encryptedVolume, dstFiles, srcUris, taskId)
            }
            failedItem
        }, srcUris)
    }

    suspend fun wipeUris(uris: List<Uri>, rootFile: DocumentFile? = null): TaskResult<out String?> {
        return globalTask(R.string.file_op_wiping_msg, uris.size, { taskId ->
            var errorMsg: String? = null
            for (i in uris.indices) {
                yield()
                errorMsg = Wiper.wipe(this@FileOperationService, uris[i])
                if (errorMsg == null) {
                    updateNotificationProgress(taskId, i+1, uris.size)
                } else {
                    break
                }
            }
            if (errorMsg == null) {
                rootFile?.delete()
            }
            errorMsg
        })
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
        return volumeTask(R.string.file_op_export_msg, items.size, volumeId) { taskId, encryptedVolume ->
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
                    updateNotificationProgress(taskId, i+1, items.size)
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
        return volumeTask(R.string.file_op_delete_msg, items.size, volumeId) { taskId, encryptedVolume ->
            var failedItem: String? = null
            for ((i, element) in items.withIndex()) {
                yield()
                if (element.isDirectory) {
                    recursiveRemoveDirectory(encryptedVolume, element.fullPath)?.let { failedItem = it }
                } else if (!encryptedVolume.deleteFile(element.fullPath)) {
                    failedItem = element.fullPath
                }
                if (failedItem == null) {
                    updateNotificationProgress(taskId, i + 1, items.size)
                } else {
                    break
                }
            }
            failedItem
        }.failedItem // treat cancellation as success
    }

    private suspend fun recursiveCountChildElements(rootDirectory: DocumentFile): Int {
        yield()
        val children = rootDirectory.listFiles()
        var count = children.size
        for (child in children) {
            if (child.isDirectory) {
                count += recursiveCountChildElements(child)
            }
        }
        return count
    }

    private suspend fun recursiveCopyVolume(
        src: DocumentFile,
        dst: DocumentFile,
        dstRootDirectory: ObjRef<DocumentFile?>?,
        taskId: Int,
        total: Int,
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
                recursiveCopyVolume(child, dstDir, null, taskId, total, progress)?.let { return it }
            }
            progress.value++
            updateNotificationProgress(taskId, progress.value, total)
        }
        return null
    }

    class CopyVolumeResult(val taskResult: TaskResult<out DocumentFile?>, val dstRootDirectory: DocumentFile?)

    suspend fun copyVolume(src: DocumentFile, dst: DocumentFile): CopyVolumeResult {
        val dstRootDirectory = ObjRef<DocumentFile?>(null)
        val result = globalTask(R.string.copy_volume_notification, null, { taskId ->
            val total = recursiveCountChildElements(src)
            updateNotificationProgress(taskId, 0, total)
            recursiveCopyVolume(src, dst, dstRootDirectory, taskId, total)
        }, {
            dstRootDirectory.value?.delete()
        })
        return CopyVolumeResult(result, dstRootDirectory.value)
    }
}
package sushi.hardcore.droidfs.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.Theme
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

object PathUtils {
    const val SEPARATOR = '/'
    const val PATH_RESOLVER_TAG = "PATH RESOLVER"

    fun getParentPath(path: String): String {
        val strippedPath = if (path.endsWith(SEPARATOR)) {
            path.substring(0, max(1, path.length - 1))
        } else {
            path
        }
        return if (strippedPath.count { it == SEPARATOR } <= 1) {
            SEPARATOR.toString()
        } else {
            strippedPath.substring(0, strippedPath.lastIndexOf(SEPARATOR))
        }
    }

    fun pathJoin(vararg strings: String): String {
        val result = StringBuilder()
        for (element in strings) {
            if (element.isNotEmpty()) {
                if (!element.startsWith(SEPARATOR) && result.last() != SEPARATOR) {
                    result.append(SEPARATOR)
                }
                result.append(element)
            }
        }
        return result.toString()
    }

    fun getRelativePath(parentPath: String, childPath: String): String {
        return childPath.substring(parentPath.length + if (parentPath.endsWith(SEPARATOR) || childPath.length == parentPath.length) {
            0
        } else {
            1
        })
    }

    fun isChildOf(childPath: String, parentPath: String): Boolean {
        if (parentPath.length > childPath.length){
            return false
        }
        return childPath.substring(0, parentPath.length) == parentPath
    }

    fun getFilenameFromURI(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            result?.let {
                val cut = it.lastIndexOf(SEPARATOR)
                if (cut != -1) {
                    result = it.substring(cut + 1)
                }
            }
        }
        return result
    }

    private val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    fun formatSize(size: Long): String {
        if (size <= 0) {
            return "0 B"
        }
        val digitGroups = (log10(size.toDouble()) / log10(1000.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1000.0.pow(digitGroups.toDouble())
        ) + " " + units[digitGroups]
    }

    fun getPackageDataFolder(context: Context): String {
        return "Android/data/${context.packageName}/"
    }

    private fun getExternalStoragesPaths(context: Context): List<String> {
        val externalPaths: MutableList<String> = ArrayList()
        ContextCompat.getExternalFilesDirs(context, null).forEach {
            if (Environment.isExternalStorageRemovable(it)) {
                val rootPath = it.path.substring(0, it.path.indexOf(getPackageDataFolder(context)+"files"))
                externalPaths.add(rootPath)
            }
        }
        return externalPaths
    }

    fun isPathOnExternalStorage(path: String, context: Context): Boolean {
        getExternalStoragesPaths(context).forEach {
            if (path.startsWith(it)){
                return true
            }
        }
        return false
    }

    fun getFullPathFromTreeUri(treeUri: Uri, context: Context): String? {
        Log.d(PATH_RESOLVER_TAG, "treeUri: $treeUri")
        if ("content".equals(treeUri.scheme, ignoreCase = true)) {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            Log.d(PATH_RESOLVER_TAG, "Document Id: $docId")
            val split: Array<String?> = docId.split(":").toTypedArray()
            val volumeId = if (split.isNotEmpty()) split[0] else null
            Log.d(PATH_RESOLVER_TAG, "Volume Id: $volumeId")
            val volumePath = getVolumePath(volumeId ?: return null, context)
            Log.d(PATH_RESOLVER_TAG, "Volume Path: $volumePath")
            val documentPath = if (split.size >= 2 && split[1] != null) split[1]!! else File.separator
            Log.d(PATH_RESOLVER_TAG, "Document Path: $documentPath")
            return if (documentPath.isNotEmpty()) {
                pathJoin(volumePath!!, documentPath)
            } else volumePath
        } else if ("file".equals(treeUri.scheme, ignoreCase = true)) {
            return treeUri.path
        }
        return null
    }

    private const val PRIMARY_VOLUME_NAME = "primary"
    private fun getVolumePath(volumeId: String, context: Context): String? {
        if (volumeId == PRIMARY_VOLUME_NAME) {
            // easy case
            return Environment.getExternalStorageDirectory().path
        }
        // external storage
        // First strategy: StorageManager.getStorageVolumes()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API is public on Android 11 and higher
            storageManager.storageVolumes.forEach { storage ->
                Log.d(PATH_RESOLVER_TAG, "StorageVolume: ${storage.uuid} ${storage.directory}")
                if (volumeId.contentEquals(storage.uuid, true)) {
                    storage.directory?.let {
                        return it.absolutePath
                    }
                }
            }
            Log.d(PATH_RESOLVER_TAG, "StorageManager failed")
        } else {
            // Before Android 11, we try reflection
            try {
                val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
                val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
                val getUuid = storageVolumeClazz.getMethod("getUuid")
                val getPath = storageVolumeClazz.getMethod("getPath")
                val result = getVolumeList.invoke(storageManager)
                val length = java.lang.reflect.Array.getLength(result!!)
                for (i in 0 until length) {
                    val storageVolumeElement = java.lang.reflect.Array.get(result, i)
                    val uuid = getUuid.invoke(storageVolumeElement)
                    if (uuid == volumeId) return getPath.invoke(storageVolumeElement) as String
                }
            } catch (e: Exception) {
                Log.d(PATH_RESOLVER_TAG, "StorageManager reflection failed")
            }
        }
        // Second strategy: Context.getExternalFilesDirs()
        for (dir in ContextCompat.getExternalFilesDirs(context, null)) {
            Log.d(PATH_RESOLVER_TAG, "External dir: $dir")
            if (Environment.isExternalStorageRemovable(dir)) {
                Log.d(PATH_RESOLVER_TAG, "isExternalStorageRemovable")
                val path = dir.path.split("/Android")[0]
                if (File(path).name == volumeId) {
                    return path
                }
            }
        }
        Log.d(PATH_RESOLVER_TAG, "getExternalFilesDirs failed")
        // Third strategy: parsing the output of mount
        // Don't risk to be killed by SELinux on newer Android versions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            try {
                val process = ProcessBuilder("mount").redirectErrorStream(true).start().apply { waitFor() }
                process.inputStream.readBytes().decodeToString().split("\n").forEach { line ->
                    if (line.startsWith("/dev/block/vold")) {
                        Log.d(PATH_RESOLVER_TAG, "mount: $line")
                        val fields = line.split(" ")
                        if (fields.size >= 3) {
                            val path = fields[2]
                            if (File(path).name == volumeId) {
                                return path
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Log.d(PATH_RESOLVER_TAG, "mount processing failed")
        }
        // Fourth strategy: guessing
        val directories = listOf("/storage/", "/mnt/media_rw/").map { File(it + volumeId) }
        listOf(File::canWrite, File::canRead, File::isDirectory).forEach { check ->
            directories.find { dir ->
                if (check(dir)) {
                    Log.d(PATH_RESOLVER_TAG, "$dir: ${check.name}")
                    true
                } else {
                    false
                }
            }?.let { return it.path }
        }
        // Fifth strategy: fail
        return null
    }

    fun recursiveRemoveDirectory(rootDirectory: File): Boolean {
        rootDirectory.listFiles()?.forEach { item ->
            if (item.isDirectory) {
                if (!recursiveRemoveDirectory(item)){
                    return false
                }
            } else {
                if (!item.delete()) {
                    return false
                }
            }
        }
        return rootDirectory.delete()
    }

     fun safePickDirectory(directoryPicker: ActivityResultLauncher<Uri?>, context: Context, theme: Theme) {
        try {
            directoryPicker.launch(null)
        } catch (e: ActivityNotFoundException) {
            CustomAlertDialogBuilder(context, theme)
                .setTitle(R.string.error)
                .setMessage(R.string.open_tree_failed)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
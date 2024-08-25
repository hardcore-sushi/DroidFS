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

    private fun getExternalStoragePath(context: Context, name: String): String? {
        for (dir in ContextCompat.getExternalFilesDirs(context, null)) {
            Log.d(PATH_RESOLVER_TAG, "External dir: $dir")
            if (Environment.isExternalStorageRemovable(dir)) {
                Log.d(PATH_RESOLVER_TAG, "isExternalStorageRemovable")
                val path = dir.path.split("/Android")[0]
                if (File(path).name == name) {
                    return path
                }
            }
        }
        Log.d(PATH_RESOLVER_TAG, "getExternalFilesDirs failed")
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
                            if (File(path).name == name) {
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
        return null
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

    private const val PRIMARY_VOLUME_NAME = "primary"
    fun getFullPathFromTreeUri(treeUri: Uri, context: Context): String? {
        if ("content".equals(treeUri.scheme, ignoreCase = true)) {
            val vId = getVolumeIdFromTreeUri(treeUri)
            Log.d(PATH_RESOLVER_TAG, "Volume Id: $vId")
            var volumePath = getVolumePath(vId ?: return null, context)
            Log.d(PATH_RESOLVER_TAG, "Volume Path: $volumePath")
            if (volumePath == null) {
                volumePath = if (vId == "primary") {
                    Environment.getExternalStorageDirectory().path
                } else {
                    getExternalStoragePath(context, vId) ?: "/storage/$vId"
                }
            }
            val documentPath = getDocumentPathFromTreeUri(treeUri)!!
            Log.d(PATH_RESOLVER_TAG, "Document Path: $documentPath")
            return if (documentPath.isNotEmpty()) {
                pathJoin(volumePath!!, documentPath)
            } else volumePath
        } else if ("file".equals(treeUri.scheme, ignoreCase = true)) {
            return treeUri.path
        }
        return null
    }

    private fun getVolumePath(volumeId: String, context: Context): String? {
        return try {
            val mStorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
            val getUuid = storageVolumeClazz.getMethod("getUuid")
            val getPath = storageVolumeClazz.getMethod("getPath")
            val isPrimary = storageVolumeClazz.getMethod("isPrimary")
            val result = getVolumeList.invoke(mStorageManager)
            val length = java.lang.reflect.Array.getLength(result!!)
            for (i in 0 until length) {
                val storageVolumeElement = java.lang.reflect.Array.get(result, i)
                val uuid = getUuid.invoke(storageVolumeElement)
                val primary = isPrimary.invoke(storageVolumeElement) as Boolean
                if (primary && PRIMARY_VOLUME_NAME == volumeId) return getPath.invoke(storageVolumeElement) as String
                if (uuid == volumeId) return getPath.invoke(storageVolumeElement) as String
            }
            null
        } catch (ex: Exception) {
            null
        }
    }

    private fun getVolumeIdFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":").toTypedArray()
        return if (split.isNotEmpty()) split[0] else null
    }

    private fun getDocumentPathFromTreeUri(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split: Array<String?> = docId.split(":").toTypedArray()
        return if (split.size >= 2 && split[1] != null) split[1] else File.separator
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
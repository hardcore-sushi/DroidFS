package sushi.hardcore.droidfs.content_providers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.LoadingTask
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

object ExternalProvider {
    private const val content_type_all = "*/*"
    private var storedFiles = HashSet<Uri>()
    private fun getContentType(filename: String, previous_content_type: String?): String {
        if (content_type_all != previous_content_type) {
            var contentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(filename).extension)
            if (contentType == null) {
                contentType = content_type_all
            }
            if (previous_content_type == null) {
                return contentType
            } else if (previous_content_type != contentType) {
                return content_type_all
            }
        }
        return previous_content_type
    }

    private fun exportFile(context: Context, encryptedVolume: EncryptedVolume, file_path: String, previous_content_type: String?): Pair<Uri?, String?> {
        val fileName = File(file_path).name
        val tmpFileUri = RestrictedFileProvider.newFile(fileName)
        if (tmpFileUri != null){
            storedFiles.add(tmpFileUri)
            if (encryptedVolume.exportFile(context, file_path, tmpFileUri)) {
                return Pair(tmpFileUri, getContentType(fileName, previous_content_type))
            }
        }
        return Pair(null, null)
    }

    fun share(activity: AppCompatActivity, themeValue: String, encryptedVolume: EncryptedVolume, file_paths: List<String>) {
        var contentType: String? = null
        val uris = ArrayList<Uri>(file_paths.size)
        object : LoadingTask<String?>(activity, themeValue, R.string.loading_msg_export) {
            override suspend fun doTask(): String? {
                for (path in file_paths) {
                    val result = exportFile(activity, encryptedVolume, path, contentType)
                    contentType = if (result.first != null) {
                        uris.add(result.first!!)
                        result.second
                    } else {
                        return path
                    }
                }
                return null
            }
        }.startTask(activity.lifecycleScope) { failedItem ->
            if (failedItem == null) {
                val shareIntent = Intent()
                shareIntent.type = contentType
                if (uris.size == 1) {
                    shareIntent.action = Intent.ACTION_SEND
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    shareIntent.action = Intent.ACTION_SEND_MULTIPLE
                    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share_chooser)))
            } else {
                CustomAlertDialogBuilder(activity, themeValue)
                    .setTitle(R.string.error)
                    .setMessage(activity.getString(R.string.export_failed, failedItem))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    fun open(activity: AppCompatActivity, themeValue: String, encryptedVolume: EncryptedVolume, file_path: String) {
        object : LoadingTask<Intent?>(activity, themeValue, R.string.loading_msg_export) {
            override suspend fun doTask(): Intent? {
                val result = exportFile(activity, encryptedVolume, file_path, null)
                return if (result.first != null) {
                    Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setDataAndType(result.first, result.second)
                    }
                } else {
                    null
                }
            }
        }.startTask(activity.lifecycleScope) { openIntent ->
            if (openIntent == null) {
                CustomAlertDialogBuilder(activity, themeValue)
                    .setTitle(R.string.error)
                    .setMessage(activity.getString(R.string.export_failed, file_path))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                activity.startActivity(openIntent)
            }
        }
    }

    fun removeFilesAsync(context: Context) = GlobalScope.launch(Dispatchers.IO) {
        val success = HashSet<Uri>(storedFiles.size)
        for (uri in storedFiles) {
            if (context.contentResolver.delete(uri, null, null) == 1) {
                success.add(uri)
            }
        }
        for (uri in success) {
            storedFiles.remove(uri)
        }
    }
}
package sushi.hardcore.droidfs.content_providers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.GocryptfsVolume
import sushi.hardcore.droidfs.LoadingTask
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

object ExternalProvider {
    private const val content_type_all = "*/*"
    private var storedFiles: MutableList<Uri> = ArrayList()
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

    private fun exportFile(context: Context, gocryptfsVolume: GocryptfsVolume, file_path: String, previous_content_type: String?): Pair<Uri?, String?> {
        val fileName = File(file_path).name
        val tmpFileUri = RestrictedFileProvider.newFile(fileName)
        if (tmpFileUri != null){
            storedFiles.add(tmpFileUri)
            if (gocryptfsVolume.exportFile(context, file_path, tmpFileUri)) {
                return Pair(tmpFileUri, getContentType(fileName, previous_content_type))
            }
        }
        return Pair(null, null)
    }

    fun share(activity: AppCompatActivity, themeValue: String, gocryptfsVolume: GocryptfsVolume, file_paths: List<String>) {
        object : LoadingTask(activity, themeValue, R.string.loading_msg_export) {
            override fun doTask(activity: AppCompatActivity) {
                var contentType: String? = null
                val uris = ArrayList<Uri>()
                for (path in file_paths) {
                    val result = exportFile(activity, gocryptfsVolume, path, contentType)
                    contentType = if (result.first != null) {
                        uris.add(result.first!!)
                        result.second
                    } else {
                        stopTask {
                            CustomAlertDialogBuilder(activity, themeValue)
                                .setTitle(R.string.error)
                                .setMessage(activity.getString(R.string.export_failed, path))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        }
                        return
                    }
                }
                val shareIntent = Intent()
                shareIntent.type = contentType
                if (uris.size == 1) {
                    shareIntent.action = Intent.ACTION_SEND
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    shareIntent.action = Intent.ACTION_SEND_MULTIPLE
                    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                stopTask {
                    activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share_chooser)))
                }
            }
        }
    }

    fun open(activity: AppCompatActivity, themeValue: String, gocryptfsVolume: GocryptfsVolume, file_path: String) {
        object : LoadingTask(activity, themeValue, R.string.loading_msg_export) {
            override fun doTask(activity: AppCompatActivity) {
                val result = exportFile(activity, gocryptfsVolume, file_path, null)
                if (result.first != null) {
                    val openIntent = Intent(Intent.ACTION_VIEW)
                    openIntent.setDataAndType(result.first, result.second)
                    stopTask { activity.startActivity(openIntent) }
                } else {
                    stopTask {
                        CustomAlertDialogBuilder(activity, themeValue)
                            .setTitle(R.string.error)
                            .setMessage(activity.getString(R.string.export_failed, file_path))
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
        }
    }

    fun removeFiles(context: Context) {
        Thread{
            val success = ArrayList<Uri>()
            for (uri in storedFiles){
                if (context.contentResolver.delete(uri, null, null) == 1){
                    success.add(uri)
                }
            }
            for (uri in success){
                storedFiles.remove(uri)
            }
        }.start()
    }
}
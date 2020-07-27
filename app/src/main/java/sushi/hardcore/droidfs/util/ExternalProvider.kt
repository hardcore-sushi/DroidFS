package sushi.hardcore.droidfs.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.provider.RestrictedFileProvider
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.net.URLConnection
import java.util.*

object ExternalProvider {
    private const val content_type_all = "*/*"
    private var storedFiles: MutableList<Uri> = ArrayList()
    private fun getContentType(filename: String, previous_content_type: String?): String? {
        if (content_type_all != previous_content_type) {
            var contentType = URLConnection.guessContentTypeFromName(filename)
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
            if (gocryptfsVolume.export_file(context, file_path, tmpFileUri)) {
                return Pair(tmpFileUri, getContentType(fileName, previous_content_type))
            }
        }
        ColoredAlertDialogBuilder(context)
                .setTitle(R.string.error)
                .setMessage(context.getString(R.string.export_failed, file_path))
                .setPositiveButton(R.string.ok, null)
                .show()
        return Pair(null, null)
    }

    fun share(context: Context, gocryptfsVolume: GocryptfsVolume, file_paths: List<String>) {
        var contentType: String? = null
        val uris = ArrayList<Uri>()
        for (path in file_paths) {
            val result = exportFile(context, gocryptfsVolume, path, contentType)
            contentType = if (result.first != null) {
                uris.add(result.first!!)
                result.second
            } else {
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
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_chooser)))
    }

    fun open(context: Context, gocryptfsVolume: GocryptfsVolume, file_path: String) {
        val result = exportFile(context, gocryptfsVolume, file_path, null)
        result.first?.let {
            val openIntent = Intent()
            openIntent.action = Intent.ACTION_VIEW
            openIntent.setDataAndType(result.first, result.second)
            context.startActivity(openIntent)
        }
    }

    fun removeFiles(context: Context) {
        Thread{
            for (uri in storedFiles) {
                if (Wiper.wipe(context, uri)){
                    storedFiles.remove(uri)
                }
            }
        }.start()
    }
}
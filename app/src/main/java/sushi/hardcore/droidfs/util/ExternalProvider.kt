package sushi.hardcore.droidfs.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.provider.TemporaryFileProvider
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.io.File
import java.net.URLConnection
import java.util.*

object ExternalProvider {
    private const val content_type_all = "*/*"
    private var cached_files: MutableList<Uri> = ArrayList()
    private fun get_content_type(filename: String, previous_content_type: String?): String? {
        if (content_type_all != previous_content_type) {
            var content_type = URLConnection.guessContentTypeFromName(filename)
            if (content_type == null) {
                content_type = content_type_all
            }
            if (previous_content_type == null) {
                return content_type
            } else if (previous_content_type != content_type) {
                return content_type_all
            }
        }
        return previous_content_type
    }

    private fun export_file(context: Context, gocryptfsVolume: GocryptfsVolume, file_path: String, previous_content_type: String?): Export_file_result {
        val filename = File(file_path).name
        val tmp_file_uri = TemporaryFileProvider.createFile(context, filename)
        cached_files.add(tmp_file_uri)
        if (gocryptfsVolume.export_file(context, file_path, tmp_file_uri)) {
            return Export_file_result(tmp_file_uri, get_content_type(filename, previous_content_type))
        }
        ColoredAlertDialog(context)
                .setTitle(R.string.error)
                .setMessage(context.getString(R.string.export_failed, file_path))
                .setPositiveButton(R.string.ok, null)
                .show()
        return Export_file_result(null, null)
    }

    fun share(context: Context, gocryptfsVolume: GocryptfsVolume, file_paths: List<String>) {
        var content_type: String? = null
        val uris = ArrayList<Uri>()
        for (path in file_paths) {
            val result = export_file(context, gocryptfsVolume, path, content_type)
            content_type = if (result.uri == null) {
                return
            } else {
                uris.add(result.uri!!)
                result.content_type
            }
        }
        val shareIntent = Intent()
        shareIntent.type = content_type
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
        val result = export_file(context, gocryptfsVolume, file_path, null)
        result.uri?.let {
            val openIntent = Intent()
            openIntent.action = Intent.ACTION_VIEW
            openIntent.setDataAndType(result.uri, result.content_type)
            context.startActivity(openIntent)
        }
    }

    fun clear_cache(context: Context) {
        Thread{
            for (uri in cached_files) {
                if (Wiper.wipe(context, uri)){
                    cached_files.remove(uri)
                }
            }
        }.start()
    }

    private class Export_file_result(var uri: Uri?, var content_type: String?)
}
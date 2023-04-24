package sushi.hardcore.droidfs.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.ceil

object Wiper {
    fun wipe(byteBuffer: ByteBuffer) {
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), Byte.MIN_VALUE)
        } else {
            for (i in 0 until byteBuffer.limit()) {
                byteBuffer.put(i, Byte.MIN_VALUE)
            }
        }
    }

    private const val buff_size = 4096
    fun wipe(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.let {
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            val size = cursor.getLong(sizeIndex)
            cursor.close()
            try {
                var os = context.contentResolver.openOutputStream(uri)!!
                val buff = ByteArray(buff_size)
                Arrays.fill(buff, 0.toByte())
                val writes = ceil(size.toDouble() / buff_size).toInt()
                for (i in 0 until Constants.WIPE_PASSES) {
                    for (j in 0 until writes) {
                        os.write(buff)
                    }
                    if (i < Constants.WIPE_PASSES - 1) {
                        //reopening to flush and seek
                        os.close()
                        os = context.contentResolver.openOutputStream(uri)!!
                    }
                }
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: UnsupportedOperationException){
                    if (DocumentFile.fromSingleUri(context, uri)?.delete() != true) {
                        (os as FileOutputStream).channel.truncate(0) //truncate to 0 if cannot delete
                    }
                }
                os.close()
                return null
            } catch (e: Exception) {
                return e.message
            }
        }
        return context.getString(R.string.query_cursor_null_error_msg)
    }
    fun wipe(file: File): String? {
        val size = file.length()
        try {
            var os = FileOutputStream(file)
            val buff = ByteArray(buff_size)
            Arrays.fill(buff, 0.toByte())
            val writes = ceil(size.toDouble() / buff_size).toInt()
            for (i in 0 until Constants.WIPE_PASSES) {
                for (j in 0 until writes) {
                    os.write(buff)
                }
                if (i < Constants.WIPE_PASSES - 1) {
                    //reopening to flush and seek
                    os.close()
                    os = FileOutputStream(file)
                }
            }
            try {
                file.delete()
            } catch (e: UnsupportedOperationException){
                os.channel.truncate(0) //truncate to 0 if cannot delete
            }
            os.close()
            return null
        } catch (e: Exception) {
            return e.message
        }
    }
}
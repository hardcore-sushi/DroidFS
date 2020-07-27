package sushi.hardcore.droidfs.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.EditText
import sushi.hardcore.droidfs.ConstValues.Companion.wipe_passes
import java.io.*
import java.lang.Exception
import java.lang.StringBuilder
import java.lang.UnsupportedOperationException
import java.util.*
import kotlin.math.ceil

object Wiper {
    private const val buff_size = 4096
    fun wipe(context: Context, uri: Uri): Boolean {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.let {
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            val size = cursor.getLong(sizeIndex)
            cursor.close()
            try {
                var os = context.contentResolver.openOutputStream(uri)
                val buff = ByteArray(buff_size)
                Arrays.fill(buff, 0.toByte())
                val writes = ceil(size.toDouble() / buff_size).toInt()
                for (i in 0 until wipe_passes) {
                    for (j in 0 until writes) {
                        os!!.write(buff)
                    }
                    if (i < wipe_passes - 1) {
                        //reopening to flush and seek
                        os!!.close()
                        os = context.contentResolver.openOutputStream(uri)
                    }
                }
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: UnsupportedOperationException){
                    (os as FileOutputStream).channel.truncate(0) //truncate to 0 if cannot delete
                }
                os!!.close()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }
    @JvmStatic
    fun wipe(file: File): Boolean{
        val size = file.length()
        try {
            var os = FileOutputStream(file)
            val buff = ByteArray(buff_size)
            Arrays.fill(buff, 0.toByte())
            val writes = ceil(size.toDouble() / buff_size).toInt()
            for (i in 0 until wipe_passes) {
                for (j in 0 until writes) {
                    os.write(buff)
                }
                if (i < wipe_passes - 1) {
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
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    private fun randomString(minSize: Int, maxSize: Int): String {
        val r = Random()
        val sb = StringBuilder()
        val length = r.nextInt(maxSize-minSize)+minSize
        for (i in 0..length){
            sb.append((r.nextInt(94)+32).toChar())
        }
        return sb.toString()
    }
    fun wipeEditText(editText: EditText){
        if (editText.text.isNotEmpty()){
            editText.setText(randomString(editText.text.length, editText.text.length*3))
        }
    }
}
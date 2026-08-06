package sushi.hardcore.droidfs.util

import android.widget.EditText
import android.text.TextUtils
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.*

object UIUtils {
    fun encodeEditTextContent(editText: EditText): ByteArray {
        val charArray = CharArray(editText.text.length)
        TextUtils.getChars(editText.text, 0, editText.text.length, charArray, 0)
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(charArray))
        Arrays.fill(charArray, Char.MIN_VALUE)
        val byteArray = ByteArray(byteBuffer.remaining())
        byteBuffer.get(byteArray)
        Wiper.wipe(byteBuffer)
        return byteArray
    }
}

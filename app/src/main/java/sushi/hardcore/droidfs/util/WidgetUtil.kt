package sushi.hardcore.droidfs.util

import android.widget.EditText
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.*

object WidgetUtil {
    fun editTextContentEncode(editText: EditText): ByteArray {
        val charArray = CharArray(editText.text.length)
        editText.text.getChars(0, editText.text.length, charArray, 0)
        val byteArray = StandardCharsets.UTF_8.encode(
            CharBuffer.wrap(charArray)
        ).array()
        Arrays.fill(charArray, Char.MIN_VALUE)
        return byteArray
    }
}
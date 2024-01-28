package sushi.hardcore.droidfs.util

import android.content.Context
import android.view.Menu
import android.widget.EditText
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.R
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.*

object UIUtils {
    fun encodeEditTextContent(editText: EditText): ByteArray {
        val charArray = CharArray(editText.text.length)
        editText.text.getChars(0, editText.text.length, charArray, 0)
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(charArray))
        Arrays.fill(charArray, Char.MIN_VALUE)
        val byteArray = ByteArray(byteBuffer.remaining())
        byteBuffer.get(byteArray)
        Wiper.wipe(byteBuffer)
        return byteArray
    }

    class MenuIconColor(
        private val context: Context,
        private val menu: Menu,
        private val color: Int
    ) {
        fun applyTo(menuItemId: Int, drawableId: Int) {
            menu.findItem(menuItemId)?.let {
                it.icon = ContextCompat.getDrawable(context, drawableId)?.apply {
                    setTint(color)
                }
            }
        }
    }

    fun getMenuIconNeutralTint(context: Context, menu: Menu) = MenuIconColor(
        context, menu,
        ContextCompat.getColor(context, R.color.neutralIconTint),
    )
}

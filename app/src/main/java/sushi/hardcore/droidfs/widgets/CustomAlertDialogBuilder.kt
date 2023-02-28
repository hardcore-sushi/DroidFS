package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.TypedValue
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.Theme

open class CustomAlertDialogBuilder(context: Context, theme: Theme) : AlertDialog.Builder(
    context, if (theme.black) {
            when (theme.color) {
                "red" -> R.style.BlackRedDialog
                "blue" -> R.style.BlackBlueDialog
                "yellow" -> R.style.BlackYellowDialog
                "orange" -> R.style.BlackOrangeDialog
                "purple" -> R.style.BlackPurpleDialog
                "pink" -> R.style.BlackPinkDialog
                else -> R.style.BlackGreenDialog
            }
        } else {
            when (theme.color) {
                "red" -> R.style.DarkRedDialog
                "blue" -> R.style.DarkBlueDialog
                "yellow" -> R.style.DarkYellowDialog
                "orange" -> R.style.DarkOrangeDialog
                "purple" -> R.style.DarkPurpleDialog
                "pink" -> R.style.DarkPinkDialog
                else -> R.style.DarkGreenDialog
            }
        }
) {
    private var keepFullScreen = false

    fun keepFullScreen(): AlertDialog.Builder {
        keepFullScreen = true
        return this
    }

    override fun show(): AlertDialog {
        val dialog = create()
        dialog.show()
        if (keepFullScreen){
            dialog.window?.let {
                WindowInsetsControllerCompat(it, it.decorView)
                    .hide(WindowInsetsCompat.Type.statusBars())
                it.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        }
        return dialog
    }

    override fun create(): AlertDialog {
        val dialog = super.create()
        dialog.setOnShowListener {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
            for (i in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                dialog.getButton(i).setTextColor(typedValue.data)
            }
        }
        if (keepFullScreen){
            dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        return dialog
    }
}
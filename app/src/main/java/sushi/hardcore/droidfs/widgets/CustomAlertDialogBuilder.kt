package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.TypedValue
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import sushi.hardcore.droidfs.R

class CustomAlertDialogBuilder(context: Context, theme: String) : AlertDialog.Builder(
    context, when (theme) {
        "black_green" -> R.style.BlackGreenDialog
        "dark_red" -> R.style.DarkRedDialog
        "black_red" -> R.style.BlackRedDialog
        "dark_blue" -> R.style.DarkBlueDialog
        "black_blue" -> R.style.BlackBlueDialog
        "dark_yellow" -> R.style.DarkYellowDialog
        "black_yellow" -> R.style.BlackYellowDialog
        "dark_orange" -> R.style.DarkOrangeDialog
        "black_orange" -> R.style.BlackOrangeDialog
        "dark_purple" -> R.style.DarkPurpleDialog
        "black_purple" -> R.style.BlackPurpleDialog
        else -> R.style.DarkGreenDialog
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
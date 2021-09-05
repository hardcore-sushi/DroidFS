package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class ColoredAlertDialogBuilder: AlertDialog.Builder {
    constructor(context: Context): super(context)
    constructor(context: Context, themeResId: Int): super(context, themeResId)

    private var keepFullScreen = false

    fun keepFullScreen(): AlertDialog.Builder {
        keepFullScreen = true
        return this
    }

    private fun applyColor(dialog: AlertDialog){
        dialog.setOnShowListener{
            val themeColor = ThemeColor.getThemeColor(context)
            for (i in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)){
                dialog.getButton(i).setTextColor(themeColor)
            }
        }
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
        applyColor(dialog)
        if (keepFullScreen){
            dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        return dialog
    }
}
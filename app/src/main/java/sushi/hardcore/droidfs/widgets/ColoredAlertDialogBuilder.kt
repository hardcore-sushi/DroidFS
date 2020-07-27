package sushi.hardcore.droidfs.widgets

import androidx.appcompat.app.AlertDialog
import android.content.Context

class ColoredAlertDialogBuilder: AlertDialog.Builder {
    constructor(context: Context): super(context)
    constructor(context: Context, themeResId: Int): super(context, themeResId)
    private fun applyColor(dialog: AlertDialog){
        dialog.setOnShowListener{
            val themeColor = ThemeColor.getThemeColor(context)
            for (i in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)){
                dialog.getButton(i).setTextColor(themeColor)
            }
        }
    }
    override fun show(): AlertDialog? {
        val dialog = super.create()
        applyColor(dialog)
        dialog.show()
        return null
    }

    override fun create(): AlertDialog {
        val dialog = super.create()
        applyColor(dialog)
        return dialog
    }
}
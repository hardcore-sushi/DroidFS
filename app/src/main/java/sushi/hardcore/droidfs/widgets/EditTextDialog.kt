package sushi.hardcore.droidfs.widgets

import android.app.Activity
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import sushi.hardcore.droidfs.R

class EditTextDialog(
    activity: Activity,
    private val titleId: Int,
    viewId: Int = R.layout.dialog_edit_text,
) : MaterialAlertDialogBuilder(activity) {
    val root = activity.layoutInflater.inflate(viewId, null)
    val dialogEditText = root.findViewById<EditText>(R.id.dialog_edit_text)
    private lateinit var callback: (String) -> Unit

    fun onSubmit(callback: (String) -> Unit): EditTextDialog {
        this.callback = callback
        return this
    }

    fun setSelectedText(text: CharSequence): EditTextDialog {
        with (dialogEditText) {
            setText(text)
            selectAll()
        }
        return this
    }

    override fun create(): AlertDialog {
        setTitle(titleId)
        setView(root)
        setPositiveButton(R.string.ok) { _, _ ->
            callback(dialogEditText.text.toString())
        }
        setNegativeButton(R.string.cancel, null)
        val dialog = super.create()
        dialogEditText.setOnEditorActionListener { _, _, _ ->
            dialog.dismiss()
            callback(dialogEditText.text.toString())
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        return dialog
    }
}
package sushi.hardcore.droidfs.widgets

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.DialogEditTextBinding

class EditTextDialog(
    activity: BaseActivity,
    private val titleId: Int,
    private val callback: (String) -> Unit,
): CustomAlertDialogBuilder(activity, activity.theme) {
    val binding = DialogEditTextBinding.inflate(activity.layoutInflater)

    fun setSelectedText(text: CharSequence) {
        with (binding.dialogEditText) {
            setText(text)
            selectAll()
        }
    }

    override fun create(): AlertDialog {
        setTitle(titleId)
        setView(binding.root)
        setPositiveButton(R.string.ok) { _, _ ->
            callback(binding.dialogEditText.text.toString())
        }
        setNegativeButton(R.string.cancel, null)
        val dialog = super.create()
        binding.dialogEditText.setOnEditorActionListener { _, _, _ ->
            dialog.dismiss()
            callback(binding.dialogEditText.text.toString())
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        return dialog
    }
}
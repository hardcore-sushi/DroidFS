package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {
    override fun performClick() {
        if (!isEnabled || !isSelectable) return
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(entries, findIndexOfValue(value)) { dialog, which ->
                val entryValue = entryValues[which].toString()
                if (callChangeListener(entryValue)) {
                    value = entryValue
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

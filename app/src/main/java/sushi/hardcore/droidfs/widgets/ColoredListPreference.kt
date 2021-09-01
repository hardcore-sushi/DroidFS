package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.adapters.DialogSingleChoiceAdapter

class ColoredListPreference: ListPreference {
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    override fun onAttached() {
        super.onAttached()
        summary = entries[entryValues.indexOf(getPersistedString(value))]
    }

    override fun onClick() {
        ColoredAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(DialogSingleChoiceAdapter(context, entries.map { s -> s.toString() }), entryValues.indexOf(getPersistedString(value))) { dialog, which ->
                dialog.dismiss()
                summary = entries[which].toString()
                persistString(entryValues[which].toString())
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
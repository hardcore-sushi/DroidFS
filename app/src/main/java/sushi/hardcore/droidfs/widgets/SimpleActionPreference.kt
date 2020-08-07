package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference

class SimpleActionPreference: Preference {
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)
    var onClick: ((SimpleActionPreference) -> Unit)? = null

    override fun onClick() {
        onClick?.let { it(this) }
    }
}
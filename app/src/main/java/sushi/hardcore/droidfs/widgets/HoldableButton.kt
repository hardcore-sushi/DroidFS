package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

class HoldableButton: AppCompatImageView {
    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { init() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { init() }
    lateinit var onClick: () -> Unit

    private fun init() {
        setOnTouchListener{ view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                view.performClick()
                isPressed = true
                onClick()
            }
            true
        }
    }

    fun release() {
        isPressed = false
    }
}
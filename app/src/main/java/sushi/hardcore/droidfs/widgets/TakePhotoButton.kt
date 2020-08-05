package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

class TakePhotoButton: AppCompatImageView {
    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { init() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { init() }
    lateinit var onClick: ()->Unit

    private fun init(){
        setOnTouchListener{ _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> onClick()
                MotionEvent.ACTION_UP -> isPressed = true
            }
            true
        }
    }

    fun onPhotoTaken(){
        isPressed = false
    }
}
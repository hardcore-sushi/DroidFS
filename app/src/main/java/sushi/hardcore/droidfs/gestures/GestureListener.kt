package sushi.hardcore.droidfs.gestures

import android.view.MotionEvent
import android.view.View


open class GestureListener {  // interface GestureListener

    open fun onTouch(view: View, event: MotionEvent): Boolean {
        return false
    }

    open fun onDown(event: MotionEvent): Boolean {
        return false
    }

    open fun onSingleTapUp(event: MotionEvent): Boolean {
        return false
    }

    open fun onHorizontalSwipe(deltaX: Float): Boolean {
        return false
    }

    open fun onVerticalSwipe(deltaY: Float): Boolean {
        return false
    }

    open fun onHorizontalScroll(event: MotionEvent, deltaX: Float): Boolean {
        return false
    }

    open fun onVerticalScroll(event: MotionEvent, deltaY: Float): Boolean {
        return false
    }

}


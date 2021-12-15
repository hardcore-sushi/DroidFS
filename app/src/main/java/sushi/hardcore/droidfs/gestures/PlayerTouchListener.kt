package sushi.hardcore.droidfs.gestures

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs


abstract class PlayerTouchListener(ctx: Context) : View.OnTouchListener, GestureListener() {
    companion object {
        const val SENSITIVITY = 100 // put into preferences?
        const val SENSITIVITY_BIG = 300
        const val JUMP_SPAN = 5000
        const val JUMP_SPAN_BIG = 15000
    }

    var gestureDetector: GestureDetector

    init {
        gestureDetector = GestureDetector(ctx, SimpleGestureListener())
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    private inner class SimpleGestureListener : SimpleOnGestureListener() {

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val deltaX: Float = e2.x - e1.x
            val deltaY: Float = e2.y - e1.y
            return if (abs(deltaX) > SENSITIVITY && abs(deltaX) > abs(deltaY)) {
                onHorizontalScroll(e2, deltaX)
            } else if (abs(deltaY) > SENSITIVITY) {
                onVerticalScroll(e2, deltaY)
            } else {
                false
            }
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val deltaX: Float = e2.x - e1.x
            val deltaY: Float = e2.y - e1.y
            return if (abs(deltaX) > SENSITIVITY && abs(deltaX) > abs(deltaY)) {
                onHorizontalSwipe(deltaX)
            } else if (abs(deltaY) > SENSITIVITY) {
                onVerticalSwipe(deltaY)
            } else {
                false
            }
        }

    }

}
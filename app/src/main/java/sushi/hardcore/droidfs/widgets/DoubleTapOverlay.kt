package sushi.hardcore.droidfs.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import sushi.hardcore.droidfs.R

class DoubleTapOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        const val CYCLE_DURATION = 750L
    }

    private var rootLayout: ConstraintLayout
    private var indicatorContainer: LinearLayout
    private var circleClipTapView: CircleClipTapView
    private var trianglesContainer: LinearLayout
    private var secondsTextView: TextView
    private var icon1: ImageView
    private var icon2: ImageView
    private var icon3: ImageView
    private var secondsOffset = 0

    private var isForward: Boolean = true
        set(value) {
            // Mirror triangles depending on seek direction
            trianglesContainer.rotation = if (value) 0f else 180f
            field = value
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.double_tap_overlay, this, true)
        rootLayout = findViewById(R.id.root_constraint_layout)
        indicatorContainer = findViewById(R.id.indicators_container)
        circleClipTapView = findViewById(R.id.circle_clip_tap_view)
        trianglesContainer = findViewById(R.id.triangle_container)
        secondsTextView = findViewById(R.id.seconds_textview)
        icon1 = findViewById(R.id.icon_1)
        icon2 = findViewById(R.id.icon_2)
        icon3 = findViewById(R.id.icon_3)

        circleClipTapView.performAtEnd = {
            visibility = View.INVISIBLE
            secondsOffset = 0
            stop()
        }
    }

    /**
     * Starts the triangle animation
     */
    private fun start() {
        stop()
        firstAnimator.start()
    }

    /**
     * Stops the triangle animation
     */
    private fun stop() {
        firstAnimator.cancel()
        secondAnimator.cancel()
        thirdAnimator.cancel()
        fourthAnimator.cancel()
        fifthAnimator.cancel()
        reset()
    }

    private fun reset() {
        icon1.alpha = 0f
        icon2.alpha = 0f
        icon3.alpha = 0f
    }

    private val firstAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).setDuration(CYCLE_DURATION / 5).apply {
            doOnStart {
                icon1.alpha = 0f
                icon2.alpha = 0f
                icon3.alpha = 0f
            }
            addUpdateListener {
                icon1.alpha = (it.animatedValue as Float)
            }

            doOnEnd {
                secondAnimator.start()
            }
        }
    }

    private val secondAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).setDuration(CYCLE_DURATION / 5).apply {
            doOnStart {
                icon1.alpha = 1f
                icon2.alpha = 0f
                icon3.alpha = 0f
            }
            addUpdateListener {
                icon2.alpha = (it.animatedValue as Float)
            }
            doOnEnd {
                thirdAnimator.start()
            }
        }
    }

    private val thirdAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).setDuration(CYCLE_DURATION / 5).apply {
            doOnStart {
                icon1.alpha = 1f
                icon2.alpha = 1f
                icon3.alpha = 0f
            }
            addUpdateListener {
                icon1.alpha =
                    1f - icon3.alpha // or 1f - it (t3.alpha => all three stay a little longer together)
                icon3.alpha = (it.animatedValue as Float)
            }
            doOnEnd {
                fourthAnimator.start()
            }
        }

    }

    private val fourthAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).setDuration(CYCLE_DURATION / 5).apply {
            doOnStart {
                icon1.alpha = 0f
                icon2.alpha = 1f
                icon3.alpha = 1f
            }
            addUpdateListener {
                icon2.alpha = 1f - (it.animatedValue as Float)
            }
            doOnEnd {
                fifthAnimator.start()
            }

        }
    }

    private val fifthAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).setDuration(CYCLE_DURATION / 5).apply {
            doOnStart {
                icon1.alpha = 0f
                icon2.alpha = 0f
                icon3.alpha = 1f
            }
            addUpdateListener {
                icon3.alpha = 1f - (it.animatedValue as Float)
            }
            doOnEnd {
                firstAnimator.start()
            }
        }
    }

    private fun changeConstraints(forward: Boolean) {
        val constraintSet = ConstraintSet()
        with(constraintSet) {
            clone(rootLayout)
            if (forward) {
                clear(indicatorContainer.id, ConstraintSet.START)
                connect(indicatorContainer.id, ConstraintSet.END,
                    ConstraintSet.PARENT_ID, ConstraintSet.END)
            } else {
                clear(indicatorContainer.id, ConstraintSet.END)
                connect(indicatorContainer.id, ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.START)
            }
            start()
            applyTo(rootLayout)
        }
    }

    fun showAnimation(forward: Boolean, x: Float, y: Float) {
        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            start()
        }
        if (forward xor isForward) {
            changeConstraints(forward)
            isForward = forward
            secondsOffset = 0
        }
        secondsOffset += DoubleTapPlayerView.SEEK_SECONDS
        secondsTextView.text = context.getString(
            if (forward)
                R.string.seek_seconds_forward
            else
                R.string.seek_seconds_backward
            , secondsOffset
        )

        // Cancel ripple and start new without triggering overlay disappearance
        // (resetting instead of ending)
        circleClipTapView.resetAnimation {
            circleClipTapView.updatePosition(x, y)
        }
    }
}